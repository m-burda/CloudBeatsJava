package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.config.DropboxClientProperties;
import com.cloudbeats.db.entities.StoredFolder;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.dto.FolderDto;
import com.cloudbeats.dto.Song;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.AudioProcessingService;
import com.cloudbeats.services.FileManagementService;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import jakarta.transaction.Transactional;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class DropboxStorageService extends ExternalMediaStorageService {
    private final DropboxClientProperties clientProperties;
    private final AudioProcessingService audioProcessingService;

    public DropboxStorageService(
            ApplicationUserRepository userRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            DropboxClientProperties clientProperties,
            AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository,
            FileManagementService fileManagementService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils
    ) {
        super(
                userRepository,
                folderRepository,
                fileRepository,
                artistRepository,
                fileManagementService,
                authorizedClientManager,
                securityUtils
        );
        this.clientProperties = clientProperties;
        this.audioProcessingService = audioProcessingService;
    }

    @Transactional
    private DbxClientV2 getDbxClient() {
//        var account = mediaStorageAccountRepository.findByUserIdAndProvider(userId, getProvider())
//                .orElseThrow(() -> new IllegalStateException("No Dropbox account found for user: " + userId));

        var config = new DbxRequestConfig(clientProperties.getClientId());
        var authClient = getAuthorizedClient();
        var credential = new DbxCredential(
                authClient.getAccessToken().getTokenValue(),
                authClient.getAccessToken().getExpiresAt().getEpochSecond(),
                authClient.getRefreshToken().getTokenValue(),
                clientProperties.getClientId(),
                clientProperties.getClientSecret()
        );

        return new DbxClientV2(config, credential);
    }

    @Override
    public Provider getProvider() {
        return Provider.dropbox;
    }

    public FolderContentsDto listFiles(String folderId, boolean cached) {
        if (cached) {
            Optional<FolderContentsDto> cachedResult = getFolderContentsFromCache(folderId);
            if (cachedResult.isPresent()) {
                return cachedResult.get();
            }
        }

        DbxClientV2 client = getDbxClient();

        StoredFolder parentFolder = getOrCreateFolder(securityUtils.getCurrentUserId(), folderId);

        try {
            ListFolderResult folderData = client.files()
                    .listFolderBuilder(parentFolder.getPath() == null ? "" : parentFolder.getPath())
                    .withIncludeMediaInfo(true)
                    .start();

            List<FolderDto> folders = new ArrayList<>();
            List<Song> songs = new ArrayList<>();

            folderData.getEntries().forEach(entry -> {
                if (entry instanceof FolderMetadata folder) {
                    folders.add(new FolderDto(folder.getName(), getProvider(), folder.getPathLower(), folder.getId()));
                } else if (entry instanceof FileMetadata file) {
                    OffsetDateTime serverModified = file.getServerModified() != null
                            ? file.getServerModified().toInstant().atOffset(ZoneOffset.UTC)
                            : null;
                    songs.add(new Song(file.getName(), List.of(), getProvider(), file.getPathLower(), file.getId(), file.getPreviewUrl(), null, serverModified, null));
                }
            });

            FolderContentsDto contents = new FolderContentsDto(folders, songs);
            updateFolderContents(folderId, contents);

            return enrichWithCachedMetadata(folderId, contents);

        } catch (DbxException e) {
            throw new IllegalArgumentException("Failed to list Dropbox files: " + e.getMessage());
        }
    }

    @Override
    public AudioFileMetadataDto getOrUpdateAudioMetadata(String fileId) {
        Optional<AudioFileMetadataDto> optionalCachedMetadata = getMetadataFromCache(fileId);
        if (optionalCachedMetadata.isPresent()) {
            return optionalCachedMetadata.get();
        }

        DbxClientV2 client = getDbxClient();

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

            UUID userId = securityUtils.getCurrentUserId();

            var extractedDto = audioProcessingService.extractAudioMetadata(fileId, tempFile);
            var metadata = convertMetadata(extractedDto);

            // TODO extract this to a separate method. Better to cache separately
            String previewUrl = getFilePreviewUrl(fileId);
            metadata.setPreviewUrl(previewUrl);
            updateFileMetadata(fileId, metadata);

            return toAudioFileMetadataDto(metadata);

        } catch (Exception e) {
            throw new RuntimeException("Metadata extraction failed", e);
        }
    }

    @Override
    protected String getFilePreviewUrl(String fileId) {
        try {
            return getDbxClient().files().getTemporaryLink(fileId).getLink();
        } catch (DbxException e) {
            throw new RuntimeException("Failed to refresh Dropbox preview URL", e);
        }
    }
}
