package com.cloudbeats.services.externalMediaStorage;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.cloudbeats.db.config.OneDriveClientProperties;
import com.cloudbeats.db.entities.AudioFileMetadata;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.models.FileType;
import com.cloudbeats.models.FolderEntry;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.ApplicationUserRepository;
import com.cloudbeats.repositories.ArtistRepository;
import com.cloudbeats.repositories.FileRepository;
import com.cloudbeats.repositories.FolderRepository;
import com.cloudbeats.repositories.MediaStorageAccountRepository;
import com.cloudbeats.services.AudioProcessingService;
import com.cloudbeats.services.FileManagementService;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.RefreshTokenParameters;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.models.DriveItem;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OneDriveStorageService extends ExternalMediaStorageService {
    private final MediaStorageAccountRepository mediaStorageAccountRepository;
    private final FileRepository fileRepository;
    private final AudioProcessingService audioProcessingService;

    private final OneDriveClientProperties clientProperties;
    private final ClientSecretCredential credential;
    private final String[] scopes = new String[]{"Files.Read", "Files.Read.All", "User.Read", "offline_access"};

    public OneDriveStorageService(
            ApplicationUserRepository userRepository,
            MediaStorageAccountRepository mediaStorageAccountRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            OneDriveClientProperties clientProperties,
            AudioProcessingService audioProcessingService,
            ArtistRepository artistRepository,
            FileManagementService fileManagementService
    ) {
        super(userRepository, folderRepository, fileRepository, artistRepository, fileManagementService);
        this.mediaStorageAccountRepository = mediaStorageAccountRepository;
        this.fileRepository = fileRepository;
        this.audioProcessingService = audioProcessingService;
        this.credential = new ClientSecretCredentialBuilder()
                .clientId(clientProperties.getClientId())
                .tenantId(clientProperties.getTenantId())
                .clientSecret(clientProperties.getClientSecret())
                .build();
        this.clientProperties = clientProperties;
    }

    @Transactional
    private GraphServiceClient getGraphClient(UUID userId) {
        var account = mediaStorageAccountRepository.findByUserIdAndProvider(userId, getProvider())
                .orElseThrow(() -> new IllegalStateException("No OneDrive account found for user: " + userId));

        boolean isAccessTokenExpired = account.getTokenExpiresAt() == null ||
                account.getTokenExpiresAt().toInstant().isBefore(Instant.now().plusSeconds(60));

        if (isAccessTokenExpired) {
            refreshAccessToken(account);
        }

        TokenCredential credential = request ->
                Mono.just(new AccessToken(account.getAccessToken(), account.getTokenExpiresAt().toInstant().atOffset(ZoneOffset.UTC)));

        return new GraphServiceClient(credential, scopes);
    }

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

    @Override
    @Transactional
    public List<FolderEntry> listFiles(UUID userId, String externalUserId, String folderId) {
        List<FolderEntry> cachedResult = getFolderContentsFromCache(userId, folderId);
        if (!cachedResult.isEmpty()) {
            return cachedResult;
        }

        GraphServiceClient graphClient = getGraphClient(userId);
        String driveId = "me";

        var childrenResponse = graphClient.drives().byDriveId("me").items().byDriveItemId("root").children().get(config -> {
            config.queryParameters.select = new String[]{"id", "name", "folder", "audio", "file"};
        });

        List<DriveItem> items = childrenResponse != null && childrenResponse.getValue() != null
                ? childrenResponse.getValue()
                : List.of();

        // Filter directly by mimeType because dogwater OneDrive won't generate metadata
        List<FolderEntry> entries = items.stream()
                .filter(item ->
                        item.getName() != null
                                && (item.getFolder() != null
                                || (item.getFile() != null
                                && Objects.equals(item.getFile().getMimeType(), "audio/mpeg")
                        ))
                )
                .map(item -> {
                    FileType fileType = item.getFolder() != null ? FileType.FOLDER : FileType.AUDIO;
                    return new FolderEntry(
                            null,
                            fileType,
                            item.getName(),
                            item.getId(),
                            item.getId(),
                            null,
                            List.of(),
                            List.of()
                    );
                })
                .collect(Collectors.toList());

        updateFolderContents(userId, folderId, entries);

        return entries;
    }

    @Override
    public AudioFileMetadata getOrUpdateAudioMetadata(UUID userId, String fileId) {
        try {
            Optional<StoredFile> cachedFile = fileRepository.findByOwnerIdAndExternalId(userId, fileId);

            if (cachedFile.isPresent() && cachedFile.get().getMetadataJson() != null) {
                return cachedFile.get().getMetadataJson();
            }

            GraphServiceClient graphClient = getGraphClient(userId);

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
            var metadata = convertMetadata(extractedDto, userId);
            metadata.setPreviewUrl(getFilePreviewUrl(userId, fileId));
            updateFileMetadata(userId, fileId, metadata);

            return metadata;
        } catch (Exception e) {
            throw new RuntimeException("Metadata extraction failed for OneDrive file", e);
        }
    }

    @Override
    protected String getFilePreviewUrl(UUID userId, String fileId) {
        GraphServiceClient graphClient = getGraphClient(userId);
        DriveItem item = graphClient.drives().byDriveId("me").root()
                .withUrl("https://graph.microsoft.com/v1.0/drives/me/items/" + fileId)
                .get();
        if (item == null || item.getAdditionalData() == null) {
            throw new RuntimeException("Failed to get OneDrive preview URL for file: " + fileId);
        }
        Object downloadUrl = item.getAdditionalData().get("@microsoft.graph.downloadUrl");
        if (downloadUrl == null) {
            throw new RuntimeException("No download URL available for OneDrive file: " + fileId);
        }
        return downloadUrl.toString();
    }
}

