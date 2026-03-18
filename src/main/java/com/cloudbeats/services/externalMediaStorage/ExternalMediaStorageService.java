package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.Artist;
import com.cloudbeats.db.entities.AudioFileMetadata;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.db.entities.StoredFolder;
import com.cloudbeats.dto.AudioMetadataExtractionDto;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.dto.FolderDto;
import com.cloudbeats.dto.Song;
import com.cloudbeats.repositories.*;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.models.FileType;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.FileManagementService;
import jakarta.transaction.Transactional;
import org.apache.tika.Tika;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public abstract class ExternalMediaStorageService {
    private final ApplicationUserRepository userRepository;
    protected final FolderRepository folderRepository;
    protected final FileRepository fileRepository;
    protected final ArtistRepository artistRepository;
    protected final FileManagementService fileManagementService;
    protected static final Duration PREVIEW_URL_EXPIRE_DURATION = Duration.ofMinutes(10);
    protected final OAuth2AuthorizedClientManager authorizedClientManager;
    protected final SecurityUtils securityUtils;

    protected ExternalMediaStorageService(
            ApplicationUserRepository userRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            ArtistRepository artistRepository,
            FileManagementService fileManagementService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils
    ) {
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.artistRepository = artistRepository;
        this.fileManagementService = fileManagementService;
        this.authorizedClientManager = authorizedClientManager;
        this.securityUtils = securityUtils;
    }

    public OAuth2AuthorizedClient getAuthorizedClient() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(getProvider().name())
                .principal(securityUtils.getAuthentication())
                .build();

        return authorizedClientManager.authorize(request);
    }

    public abstract Provider getProvider();

    public abstract FolderContentsDto listFiles(String folderId, boolean cached);

    public abstract AudioFileMetadataDto getOrUpdateAudioMetadata(String fileId);

    protected abstract String getFilePreviewUrl(String fileId);

    protected Optional<AudioFileMetadataDto> getMetadataFromCache(String fileId) {
        UUID userId = securityUtils.getCurrentUserId();
        Optional<StoredFile> cachedFile = fileRepository.findByOwnerIdAndExternalId(userId, fileId);

        if (cachedFile.isPresent() && cachedFile.get().getMetadataJson() != null) {
            AudioFileMetadata cachedMetadata = cachedFile.get().getMetadataJson();
            AudioFileMetadataDto response = toAudioFileMetadataDto(cachedMetadata);

            // TODO side effect
            if (isPreviewUrlExpired(cachedMetadata)) {
                String previewUrl = getFilePreviewUrl(fileId);
                cachedMetadata.setPreviewUrl(previewUrl);
                updateFileMetadata(fileId, cachedMetadata);
            }

            return Optional.of(response);
        }
        return Optional.empty();
    }

    @Transactional
    public void updateFileMetadata(String fileId, AudioFileMetadata metadata) {
        StoredFile storedFile = fileRepository.findByOwnerIdAndExternalId(securityUtils.getCurrentUserId(), fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        storedFile.setMetadataJson(metadata);
        fileRepository.save(storedFile);
    }

    @Transactional
    public AudioFileMetadata convertMetadata(AudioMetadataExtractionDto dto) {
        // TODO srp
        AudioFileMetadata metadata = new AudioFileMetadata();
        metadata.setTitle(dto.getTitle());
        metadata.setAlbum(dto.getAlbum());
        metadata.setAlbumCoverUrl(dto.getAlbumCoverUrl());
        metadata.setGenres(dto.getGenres());
        metadata.setYear(dto.getYear());
        metadata.setDuration(dto.getDuration());

        UUID userId = securityUtils.getCurrentUserId();
        ApplicationUser userRef = userRepository.getReferenceById(userId);
        Artist artist = artistRepository.findByNameAndUserIdOrderByNameAsc(dto.getArtistName(), userId)
                .orElseGet(() -> {
                    Artist newArtist = new Artist(dto.getArtistName(), userRef);
                    return artistRepository.save(newArtist);
                });

        metadata.setAlbumArtists(List.of(artist));
        return metadata;
    }

    @Transactional
    public Optional<FolderContentsDto> getFolderContentsFromCache(String folderId) {
        var folder = folderRepository.findByOwnerIdAndProviderAndExternalId(securityUtils.getCurrentUserId(), getProvider(), folderId);

        if (folder.isEmpty() || (folder.get().getFiles().isEmpty() && folder.get().getFolders().isEmpty())) {
            return Optional.empty();
        }

        List<FolderDto> folders = folder.get().getFolders().stream()
                .map(f -> new FolderDto(
                        f.getName(),
                        getProvider(),
                        f.getExternalId(),
                        f.getExternalId()
                ))
                .sorted(Comparator.comparing(FolderDto::name))
                .collect(Collectors.toList());

        List<Song> songs = folder.get().getFiles().stream()
                .map(entry -> {
                    var metadata = entry.getMetadataJson();
                    return new Song(
                            entry.getName(),
                            metadata != null ? metadata.getAlbumArtists().stream().map(Artist::getName).toList() : List.of(),
                            getProvider(),
                            entry.getExternalId(),
                            entry.getExternalId(),
                            metadata != null ? metadata.getPreviewUrl() : "",
                            metadata != null ? metadata.getAlbumCoverUrl() : null,
                            metadata
                    );
                })
                .sorted(Comparator.comparing(Song::name))
                .collect(Collectors.toList());

        return Optional.of(new FolderContentsDto(folders, songs));
    }

    protected boolean isPreviewUrlExpired(AudioFileMetadata metadata) {
        if (metadata == null || metadata.getPreviewUrl() == null || metadata.getLastModified() == null) {
            return true;
        }

        Duration age = Duration.between(metadata.getLastModified().toInstant(), OffsetDateTime.now(ZoneOffset.UTC));
        return age.compareTo(PREVIEW_URL_EXPIRE_DURATION) > 0;
    }

    protected String getExtensionFromMime(File file) {
        Tika tika = new Tika();
        try {
            String mimeType = tika.detect(file);
            return switch (mimeType) {
                case "audio/x-flac", "audio/flac" -> ".flac";
                case "audio/mp4", "audio/x-m4a" -> ".m4a";
                case "audio/ogg", "application/ogg" -> ".ogg";
                case "audio/x-wav", "audio/wav" -> ".wav";
                default -> ".mp3";
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    protected void updateFolderContents(String folderId, FolderContentsDto contents) {
        UUID userId = securityUtils.getCurrentUserId();
        ApplicationUser currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String normalizedFolderId = folderId.equals("/") ? "" : folderId;
        var parentFolder = folderRepository.findByOwnerIdAndProviderAndExternalId(
                userId, getProvider(), normalizedFolderId
        ).orElseGet(() -> {
            var folder = new StoredFolder();
            folder.setExternalId(normalizedFolderId);
            folder.setProvider(getProvider());
            folder.setOwner(currentUser);
            folder.setName(normalizedFolderId.isEmpty() ? "root" : normalizedFolderId);
            folder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
            return folder;
        });

        parentFolder.getFolders().clear();
        parentFolder.getFiles().clear();
        parentFolder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));

        for (FolderDto folderDto : contents.folders()) {
            var folder = new StoredFolder();
            folder.setExternalId(folderDto.path());
            folder.setProvider(getProvider());
            folder.setOwner(currentUser);
            folder.setName(folderDto.name());
            folder.setParent(parentFolder);
            folder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
            parentFolder.getFolders().add(folder);
        }

        for (Song song : contents.files()) {
            var file = new StoredFile();
            file.setExternalId(song.path());
            file.setProvider(getProvider());
            file.setOwner(currentUser);
            file.setName(song.name());
            file.setFolder(parentFolder);
            file.setLastModified(OffsetDateTime.now(ZoneOffset.UTC));
            file.setType(FileType.AUDIO);
            file.setMetadataJson(song.metadata());
            parentFolder.getFiles().add(file);
        }

        folderRepository.save(parentFolder);
    }

    protected AudioFileMetadataDto toAudioFileMetadataDto(AudioFileMetadata metadata) {
        String albumCoverUrl = fileManagementService.generateAccessUrlIfExpired(
                metadata.getAlbumCoverUrl(),
                Duration.ofDays(7)
        );

        return new AudioFileMetadataDto(
                metadata.getTitle(),
                metadata.getAlbumArtists().stream().map(Artist::getName).toList(),
                metadata.getAlbum(),
                metadata.getGenres(),
                albumCoverUrl,
                metadata.getDuration(),
                metadata.getPreviewUrl()
        );
    }

    @Transactional
    public void deleteAllFoldersAndFiles() {
        UUID userId = securityUtils.getCurrentUserId();
        fileRepository.deleteAllFilesAndFoldersForUserId(userId);
    }
}
