package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.config.DropboxClientProperties;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.db.entities.StoredFolder;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FileDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.dto.FolderDto;
import com.cloudbeats.services.SongService;
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
    private final SongService songService;

    public DropboxStorageService(
            ApplicationUserRepository userRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            DropboxClientProperties clientProperties,
            AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository,
            AlbumRepository albumRepository,
            FileManagementService fileManagementService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils,
            SongService songService
    ) {
        super(
                userRepository,
                folderRepository,
                fileRepository,
                artistRepository,
                albumRepository,
                fileManagementService,
                authorizedClientManager,
                securityUtils,
                songService
        );
        this.clientProperties = clientProperties;
        this.audioProcessingService = audioProcessingService;
        this.songService = songService;
    }

    @Transactional
    private DbxClientV2 getDbxClient() {
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

        StoredFolder parentFolder = getOrCreateFolder(securityUtils.getCurrentUserId(), folderId);
        try {
            DbxClientV2 client = getDbxClient();
            ListFolderResult folderData = client.files()
                    .listFolderBuilder(parentFolder.getPath() == null ? "" : parentFolder.getPath())
                    .withIncludeMediaInfo(true)
                    .start();

            List<FolderDto> folders = new ArrayList<>();
            List<FileDto> files = new ArrayList<>();

            folderData.getEntries().forEach(entry -> {
                if (entry instanceof FolderMetadata folder) {
                    folders.add(new FolderDto(folder.getName(), getProvider(), folder.getPathLower(), folder.getId()));
                } else if (entry instanceof FileMetadata file && isAudioFile(file)) {
                    files.add(toFileDto(file));
                }
            });

            FolderContentsDto contents = new FolderContentsDto(folders, files);
            updateFolderContents(folderId, contents);

            return enrichWithCachedMetadata(folderId, contents);

        } catch (DbxException e) {
            throw new IllegalArgumentException("Failed to list Dropbox files: " + e.getMessage());
        }
    }

    private boolean isAudioFile(FileMetadata file) {
        String name = file.getName().toLowerCase();
        List<String> audioExtensions = List.of(".mp3", ".wav", ".flac", ".m4a", ".ogg");
        return audioExtensions.stream().anyMatch(name::endsWith);
    }

    private FileDto toFileDto(FileMetadata file) {
        OffsetDateTime serverModified = file.getServerModified() != null
                ? file.getServerModified().toInstant().atOffset(ZoneOffset.UTC)
                : null;
        return new FileDto(
                file.getName(),
                getProvider(),
                file.getPathLower(),
                file.getId(),
                file.getPreviewUrl(),
                serverModified,
                null
        );
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

            StoredFile sf = fileRepository.findByOwnerIdAndExternalId(securityUtils.getCurrentUserId(), fileId)
                    .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
            var extractedDto = audioProcessingService.extractAudioMetadata(fileId, tempFile);
            sf.setPreviewUrl(getFilePreviewUrl(fileId));
            updateFileMetadata(fileId, extractedDto);

            return toAudioFileMetadataDto(sf);

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
