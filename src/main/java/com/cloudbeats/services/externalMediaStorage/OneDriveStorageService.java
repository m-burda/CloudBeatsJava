package com.cloudbeats.services.externalMediaStorage;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.cloudbeats.config.OneDriveClientProperties;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.dto.*;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.InMemoryCacheService;
import com.cloudbeats.services.SongService;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.AudioProcessingService;
import com.cloudbeats.services.FileManagementService;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.RefreshTokenParameters;
import com.microsoft.graph.drives.item.items.item.preview.PreviewPostRequestBody;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.models.DriveItem;
import jakarta.transaction.Transactional;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.*;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class OneDriveStorageService extends ExternalMediaStorageService {
    private final MediaStorageAccountRepository mediaStorageAccountRepository;
    private final AudioProcessingService audioProcessingService;

    private final OneDriveClientProperties clientProperties;
    private final String[] scopes = new String[]{"Files.Read", "Files.Read.All", "User.Read", "offline_access"};

    public OneDriveStorageService(
            ApplicationUserRepository userRepository,
            MediaStorageAccountRepository mediaStorageAccountRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            OneDriveClientProperties clientProperties,
            AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository,
            FileManagementService fileManagementService,
            InMemoryCacheService cacheService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils,
            AlbumRepository albumRepository,
            SongService songService
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
                songService
        );
        this.mediaStorageAccountRepository = mediaStorageAccountRepository;
        this.audioProcessingService = audioProcessingService;
        this.clientProperties = clientProperties;
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

    @Deprecated
    private void refreshAccessToken(MediaStorageAccount account) {
        try {
            ConfidentialClientApplication app = ConfidentialClientApplication.builder(
                            clientProperties.getClientId(),
                            ClientCredentialFactory.createFromSecret(clientProperties.getClientSecret()))
                    .authority("https://login.microsoftonline.com/" + clientProperties.getTenantId())
                    .build();

            RefreshTokenParameters parameters = RefreshTokenParameters
                    .builder(Set.of(scopes), account.getRefreshToken())
                    .build();

            IAuthenticationResult result = app.acquireToken(parameters).get();

            account.setAccessToken(result.accessToken());
            account.setTokenExpiresAt(Date.from(result.expiresOnDate().toInstant()));

//            if (result.account() != null) {
////                 Rotate refresh token if SDK provides a new one via silent flow cache
//            }

            mediaStorageAccountRepository.save(account);

        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh OneDrive access token via MSAL", e);
        }
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

        return enrichWithCachedMetadata(folderId, contents);
    }

    /** Builds a bare SongDto from OneDrive provider metadata (no album cover, no preview). */
    private SongDto toProviderSongDto(DriveItem item) {
        return new SongDto(
                item.getName(),
                null,
                null,
                null,
                getProvider(),
                item.getId(),
                item.getId(),
                null,
                null,
                null
        );
    }

    @Override
    public SongDto getOrUpdateMetadata(String fileId) {
        Optional<SongDto> cached = getMetadataFromCache(fileId);
        if (cached.isPresent()) {
            return cached.get();
        }

        updateAudioMetadata(fileId);

        StoredFile sf = fileRepository.findByOwnerIdAndExternalId(securityUtils.getCurrentUserId(), fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        return toSongDtoWithPreview(sf);
    }

    @Override
    public void updateAudioMetadata(String fileId) {
        try {
            GraphServiceClient graphClient = getGraphClient();

            InputStream fileStream = graphClient.drives().byDriveId("me").root()
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
            updateMetadata(fileId, extractedDto);
        } catch (Exception e) {
            throw new RuntimeException("Metadata extraction failed for OneDrive file", e);
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

        PreviewPostRequestBody previewBody = new PreviewPostRequestBody();

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
