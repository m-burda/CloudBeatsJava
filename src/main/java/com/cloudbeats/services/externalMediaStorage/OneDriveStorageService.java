package com.cloudbeats.services.externalMediaStorage;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.cloudbeats.config.OneDriveClientProperties;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.dto.FolderDto;
import com.cloudbeats.dto.Song;
import com.cloudbeats.utils.SecurityUtils;
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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.*;
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
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils
    ) {
        super(userRepository, folderRepository, fileRepository, artistRepository, fileManagementService, authorizedClientManager, securityUtils);
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
    public FolderContentsDto listFiles(String folderId) {
        Optional<FolderContentsDto> cachedResult = getFolderContentsFromCache(folderId);
        if (cachedResult.isPresent()) {
            return cachedResult.get();
        }

        GraphServiceClient graphClient = getGraphClient();

        var childrenResponse = graphClient.drives().byDriveId("me").items().byDriveItemId("root").children().get(config -> {
            config.queryParameters.select = new String[]{"id", "name", "folder", "audio", "file"};
        });

        List<DriveItem> items = childrenResponse != null && childrenResponse.getValue() != null
                ? childrenResponse.getValue()
                : List.of();

        List<FolderDto> folders = new ArrayList<>();
        List<Song> songs = new ArrayList<>();

        // Filter directly by mimeType because dogwater OneDrive won't return metadata
        items.stream()
                .filter(item ->
                        item.getName() != null
                                && (item.getFolder() != null
                                || (item.getFile() != null
                                && Objects.equals(item.getFile().getMimeType(), "audio/mpeg")
                        ))
                )
                .forEach(item -> {
                    if (item.getFolder() != null) {
                        folders.add(new FolderDto(item.getName(), getProvider(), item.getId(), item.getId()));
                    } else {
                        songs.add(new Song(item.getName(), List.of(), getProvider(), item.getId(), item.getId(), null, null, null));
                    }
                });

        FolderContentsDto contents = new FolderContentsDto(folders, songs);
        updateFolderContents(folderId, contents);

        return contents;
    }

    @Override
    public AudioFileMetadataDto getOrUpdateAudioMetadata(String fileId) {
        try {
            UUID userId = securityUtils.getCurrentUserId();
            Optional<AudioFileMetadataDto> optionalCachedMetadata = getMetadataFromCache(fileId);
            if (optionalCachedMetadata.isPresent()) {
                return optionalCachedMetadata.get();
            }

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
            var metadata = convertMetadata(extractedDto);
            metadata.setPreviewUrl(getFilePreviewUrl(fileId));
            updateFileMetadata(fileId, metadata);

            return toAudioFileMetadataDto(metadata);
        } catch (Exception e) {
            throw new RuntimeException("Metadata extraction failed for OneDrive file", e);
        }
    }

    @Override
    protected String getFilePreviewUrl(String fileId) {
        GraphServiceClient graphClient = getGraphClient();
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

