package com.cloudbeats.services.externalMediaStorage;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.dto.*;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.InMemoryCacheService;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.AudioProcessingService;
import com.cloudbeats.services.FileManagementService;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.models.DriveItem;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.*;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class OneDriveStorageService extends ExternalMediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(OneDriveStorageService.class);
    private final AudioProcessingService audioProcessingService;
    private final String[] scopes = new String[]{"Files.Read", "Files.Read.All", "User.Read", "offline_access"};

    @Autowired
    @Lazy
    private OneDriveStorageService self;

    public OneDriveStorageService(
            ApplicationUserRepository userRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository,
            FileManagementService fileManagementService,
            InMemoryCacheService cacheService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils,
            AlbumRepository albumRepository,
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
        this.audioProcessingService = audioProcessingService;
    }

    @Transactional
    private GraphServiceClient getGraphClient() {
        var accessToken = getAuthorizedClient().getAccessToken();

        TokenCredential credential = request ->
                Mono.just(new AccessToken(
                        accessToken.getTokenValue(),
                        accessToken.getExpiresAt().atOffset(ZoneOffset.UTC)
                ));

        return new GraphServiceClient(credential, scopes);
    }

    @Override
    public Provider getProvider() {
        return Provider.onedrive;
    }

    @Transactional
    public FolderContentsDto listFiles(String folderId, boolean cached) {
        if (cached) {
            Optional<FolderContentsDto> cachedResult = getFolderContentsFromCache(folderId);
            if (cachedResult.isPresent()) {
                return cachedResult.get();
            }
        }

        GraphServiceClient graphClient = getGraphClient();

        var childrenResponse = graphClient.drives().byDriveId("me").items().byDriveItemId(folderId).children().get(config -> {
            config.queryParameters.select = new String[]{"id", "name", "folder", "audio", "file", "parentReference"};
        });

        List<DriveItem> items = childrenResponse != null && childrenResponse.getValue() != null
                ? childrenResponse.getValue()
                : List.of();

        List<FolderDto> folders = new ArrayList<>();
        List<SongDto> files = new ArrayList<>();

        // Filter directly by mimeType because dogwater OneDrive won't return metadata
        items.stream()
                .filter(item ->
                        item.getName() != null
                                && (item.getFolder() != null
                                || (item.getFile() != null && Objects.equals(item.getFile().getMimeType(), "audio/mpeg")
                        ))
                )
                .forEach(item -> {
                    String parentPath = (item.getParentReference() != null && item.getParentReference().getPath() != null)
                            ? item.getParentReference().getPath()
                            : "";
                    String cleanParent = parentPath.contains(":")
                            ? parentPath.substring(parentPath.indexOf(':') + 1)
                            : parentPath;
                    if (item.getFolder() != null) {
                        String folderPath = cleanParent.isEmpty() ? "/" + item.getName() : cleanParent + "/" + item.getName();
                        folders.add(new FolderDto(item.getName(), getProvider(), folderPath, item.getId()));
                    } else {
                        files.add(toProviderSongDto(item));
                    }
                });

        FolderContentsDto contents = new FolderContentsDto(folders, files);
        updateFolderContents(folderId, contents);

        return enrichFolderContentsWithCachedMetadata(folderId, contents);
    }

    private SongDto toProviderSongDto(DriveItem item) {
        return new SongDto(
                item.getName(),
                null,
                null,
                null,
                getProvider(),
                item.getId(),
                null,
                null,
                null
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
            self.fetchAndStoreMetadataAsync(getGraphClient(), fileId, userId);
        }

        return previewUrl;
    }

    @Async
    @Transactional
    public void fetchAndStoreMetadataAsync(GraphServiceClient client, String fileId, UUID userId) {
        try {
            InputStream fileStream = client.drives().byDriveId("me").root()
                    .withUrl("https://graph.microsoft.com/v1.0/drives/me/items/" + fileId + "/content")
                    .content()
                    .get();

            File tempFile = File.createTempFile("cloudbeats_", ".tmp");
            try (OutputStream out = new FileOutputStream(tempFile)) {
                fileStream.transferTo(out);
            }

            String extension = getExtensionFromMime(tempFile);
            File renamedFile = new File(tempFile.getAbsolutePath() + extension);
            if (tempFile.renameTo(renamedFile)) {
                tempFile = renamedFile;
            }
            var extractedDto = audioProcessingService.extractAudioMetadata(fileId, tempFile);
            updateMetadata(userId, fileId, extractedDto);
            StoredFile sf = fileRepository.findByOwnerIdAndExternalId(userId, fileId)
                    .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
            sendMetadataUpdate(toSongDto(sf), userId.toString());
        } catch (Exception e) {
            log.error("Async metadata extraction failed for OneDrive file {}", fileId, e);
            notificationService.sendError(userId.toString(),"Metadata extraction failed for file: " + fileId);
        }
    }

    @Override
    protected PreviewUrlResult fetchFilePreviewUrl(String fileId) {
        GraphServiceClient graphClient = getGraphClient();
        DriveItem item = graphClient.drives().byDriveId("me").root()
                .withUrl("https://graph.microsoft.com/v1.0/drives/me/items/" + fileId)
                .get();
        if (item == null || item.getAdditionalData() == null) {
            throw new RuntimeException("Failed to get OneDrive preview URL for file: " + fileId);
        }
        Object downloadUrl = item.getAdditionalData().get("@microsoft.graph.downloadUrl");

//        PreviewPostRequestBody previewBody = new PreviewPostRequestBody();
//        var previewUrlAlt = graphClient.drives().byDriveId("me")
//                .items().byDriveItemId(fileId)
//                .preview()
//                .post(previewBody)
//                .getGetUrl();

        if (downloadUrl == null) {
            throw new RuntimeException("No download URL available for OneDrive file: " + fileId);
        }
        String url = downloadUrl.toString();
        // This is guesswork, OneDrive does not provide expiry info
        return new PreviewUrlResult(url, Duration.ofHours(1));
    }
}
