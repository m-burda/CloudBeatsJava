package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.db.config.DropboxClientProperties;
import com.cloudbeats.db.entities.Artist;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.models.FileType;
import com.cloudbeats.models.FolderEntry;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.AudioProcessingService;
import com.cloudbeats.services.FileManagementService;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Duration;
import java.util.*;

@Service
public class DropboxStorageService extends ExternalMediaStorageService {
    private final MediaStorageAccountRepository mediaStorageAccountRepository;
    private final FileRepository fileRepository;
    private final DropboxClientProperties clientProperties;
    private final AudioProcessingService audioProcessingService;

    public DropboxStorageService(
            ApplicationUserRepository userRepository,
            MediaStorageAccountRepository mediaStorageAccountRepository,
            FolderRepository folderRepository, FileRepository fileRepository,
            DropboxClientProperties clientProperties, AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository, FileManagementService fileManagementService
    ) {
        super(userRepository, folderRepository, fileRepository, artistRepository, fileManagementService);
        this.mediaStorageAccountRepository = mediaStorageAccountRepository;
        this.fileRepository = fileRepository;
        this.clientProperties = clientProperties;
        this.audioProcessingService = audioProcessingService;
    }

    @Transactional
    private DbxClientV2 getDbxClient(UUID userId) {
        var account = mediaStorageAccountRepository.findByUserIdAndProvider(userId, getProvider())
                .orElseThrow(() -> new IllegalStateException("No Dropbox account found for user: " + userId));

        var config = new DbxRequestConfig(clientProperties.getClientId());
        var credential = new DbxCredential(
                account.getAccessToken(),
                account.getTokenExpiresAt().getTime(),
                account.getRefreshToken(),
                clientProperties.getClientId(),
                clientProperties.getClientSecret()
        );
        if (credential.aboutToExpire()) {
            try {
                credential.refresh(config);
                account.setAccessToken(credential.getAccessToken());
                account.setRefreshToken(credential.getRefreshToken());
                account.setTokenExpiresAt(new Date(credential.getExpiresAt()));
                mediaStorageAccountRepository.save(account);
            } catch (DbxException e) {
                throw new RuntimeException("Failed to refresh Dropbox token", e);
            }
        }

        return new DbxClientV2(config, credential);
    }

    @Override
    public Provider getProvider() {
        return Provider.dropbox;
    }

    @Override
    public List<FolderEntry> listFiles(UUID userId, String externalUserId, String folderId) {
        List<FolderEntry> cachedResult = getFolderContentsFromCache(userId, folderId);
        if (!cachedResult.isEmpty()) {
            return cachedResult;
        }

        DbxClientV2 client = getDbxClient(userId);

        try {
            // Dropbox uses "" for the root folder instead of "/"
            String path = folderId.equals("/") ? "" : folderId;

            ListFolderResult folderData = client.files()
                    .listFolderBuilder(path)
                    .withIncludeMediaInfo(true)
                    .start();

            List<FolderEntry> entries = folderData.getEntries().stream().map(
                    entry -> {
                        var fileType = entry instanceof FolderMetadata ? FileType.FOLDER : FileType.AUDIO;
                        return new FolderEntry(
                                entry.getPreviewUrl(),
                                fileType,
                                entry.getName(),
                                getProvider(),
                                entry.getPathLower(),
                                entry.getPathLower(),
                                null,
                                List.of(),
                                List.of()
                        );
                    }
            ).toList();

            updateFolderContents(userId, folderId, entries);

            return entries;

        } catch (DbxException e) {
            throw new IllegalArgumentException("Failed to list Dropbox files: " + e.getMessage());
        }
    }

    @Override
    public AudioFileMetadataDto getOrUpdateAudioMetadata(UUID userId, String fileId) {
        Optional<AudioFileMetadataDto> optionalCachedMetadata = getMetadataFromCache(userId, fileId);
        if (optionalCachedMetadata.isPresent()) {
            return optionalCachedMetadata.get();
        }

        DbxClientV2 client = getDbxClient(userId);

        try {
            File tempFile = File.createTempFile("cloudbeats_", ".tmp");

            try (OutputStream out = new FileOutputStream(tempFile)) {
                client.files().download(fileId).download(out);
            }

            String extension = getExtensionFromMime(tempFile);
            File renamedFile = new File(tempFile.getAbsolutePath() + extension);
            if (tempFile.renameTo(renamedFile)) {
                tempFile = renamedFile;
            }

            var extractedDto = audioProcessingService.extractAudioMetadata(fileId, tempFile);
            var metadata = convertMetadata(extractedDto, userId);

            // TODO extract this to a separate method. Better to cache separately
            if (isPreviewUrlExpired(metadata)){
                String previewUrl = getFilePreviewUrl(userId, fileId);
                metadata.setPreviewUrl(previewUrl);
            }

            updateFileMetadata(userId, fileId, metadata);

            return toAudioFileMetadataDto(metadata);

        } catch (Exception e) {
            throw new RuntimeException("Metadata extraction failed", e);
        }
    }

    @Override
    protected String getFilePreviewUrl(UUID userId, String fileId) {
        try {
            return getDbxClient(userId).files().getTemporaryLink(fileId).getLink();
        } catch (DbxException e) {
            throw new RuntimeException("Failed to refresh Dropbox preview URL", e);
        }
    }
}
