package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.config.DropboxClientProperties;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.db.entities.StoredFolder;
import com.cloudbeats.dto.*;
import com.cloudbeats.services.InMemoryCacheService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executor;


@Service
public class DropboxStorageService extends ExternalMediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(DropboxStorageService.class);
    private final DropboxClientProperties clientProperties;
    private final AudioProcessingService audioProcessingService;
    @Autowired
    @Lazy
    private DropboxStorageService self;

    public DropboxStorageService(
            ApplicationUserRepository userRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            DropboxClientProperties clientProperties,
            AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository,
            AlbumRepository albumRepository,
            FileManagementService fileManagementService,
            InMemoryCacheService cacheService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils,
            Executor taskExecutor
    ) {
        super(
                userRepository,
                folderRepository,
                fileRepository,
                artistRepository,
                albumRepository,
                fileManagementService,
                cacheService,
                authorizedClientManager,
                securityUtils,
                taskExecutor
        );
        this.clientProperties = clientProperties;
        this.audioProcessingService = audioProcessingService;
    }

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
            List<SongDto> files = new ArrayList<>();

            folderData.getEntries().forEach(entry -> {
                if (entry instanceof FolderMetadata folder) {
                    folders.add(new FolderDto(folder.getName(), getProvider(), folder.getPathLower(), folder.getId()));
                } else if (entry instanceof FileMetadata file && isAudioFile(file)) {
                    files.add(toProviderSongDto(file));
                }
            });

            FolderContentsDto contents = new FolderContentsDto(folders, files);
            updateFolderContents(folderId, contents);

            return enrichFolderContentsWithCachedMetadata(folderId, contents);

        } catch (DbxException e) {
            throw new IllegalArgumentException("Failed to list Dropbox files: " + e.getMessage());
        }
    }

    private boolean isAudioFile(FileMetadata file) {
        String name = file.getName().toLowerCase();
        List<String> audioExtensions = List.of(".mp3", ".wav", ".flac", ".m4a", ".ogg");
        return audioExtensions.stream().anyMatch(name::endsWith);
    }

    private SongDto toProviderSongDto(FileMetadata file) {
        OffsetDateTime serverModified = file.getServerModified() != null
                ? file.getServerModified().toInstant().atOffset(ZoneOffset.UTC)
                : null;
        return new SongDto(
                file.getName(),
                null,
                null,
                null,
                getProvider(),
                file.getId(),
                null,
                null,
                serverModified
        );
    }

    @Override
    public String getOrUpdateMetadata(String fileId) {
        UUID userId = securityUtils.getCurrentUserId();
        String previewUrl = getOrFetchPreviewUrl(fileId);

        boolean hasMetadata = fileRepository.findByOwnerIdAndExternalId(userId, fileId)
                .map(sf -> sf.getMetadata() != null)
                .orElse(false);

        if (!hasMetadata) {
            self.extractAndStoreMetadataAsync(getDbxClient(), fileId, userId);
        }

        return previewUrl;
    }

    @Async
    @Transactional
    public void extractAndStoreMetadataAsync(DbxClientV2 client, String fileId, UUID userId) {
        var metadata = extractAudioMetadata(client, fileId);
        updateMetadata(userId, fileId, metadata);
        StoredFile sf = fileRepository.findByOwnerIdAndExternalId(userId, fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        sendMetadataUpdate(toSongDto(sf), userId.toString());
    }

    private AudioMetadataExtractionDto extractAudioMetadata(DbxClientV2 client, String fileId){
        try {
            File tempFile = File.createTempFile("cloudbeats_", ".tmp");
            try (OutputStream out = new FileOutputStream(tempFile)) {
                client.files().download(fileId).download(out);
                String extension = getExtensionFromMime(tempFile);
                File renamedFile = new File(tempFile.getAbsolutePath() + extension);
                if (tempFile.renameTo(renamedFile)) {
                    tempFile = renamedFile;
                }
                return audioProcessingService.extractAudioMetadata(fileId, tempFile);
            }
            finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (DbxException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected PreviewUrlResult fetchFilePreviewUrl(String fileId) {
        try {
            String link = getDbxClient().files().getTemporaryLink(fileId).getLink();
            return new PreviewUrlResult(link, Duration.ofHours(4));
        } catch (DbxException e) {
            throw new RuntimeException("Failed to refresh Dropbox preview URL", e);
        }
    }
}
