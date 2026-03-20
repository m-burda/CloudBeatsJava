package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.db.entities.*;
import com.cloudbeats.dto.AudioMetadataExtractionDto;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FileDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.dto.FolderDto;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.SongService;
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
    protected final AlbumRepository albumRepository;
    protected final FileManagementService fileManagementService;
    protected static final Duration PREVIEW_URL_EXPIRE_DURATION = Duration.ofMinutes(10);
    protected final OAuth2AuthorizedClientManager authorizedClientManager;
    protected final SecurityUtils securityUtils;
    protected final SongService songService;

    protected ExternalMediaStorageService(
            ApplicationUserRepository userRepository,
            FolderRepository folderRepository,
            FileRepository fileRepository,
            ArtistRepository artistRepository,
            AlbumRepository albumRepository,
            FileManagementService fileManagementService,
            OAuth2AuthorizedClientManager authorizedClientManager,
            SecurityUtils securityUtils,
            SongService songService
    ) {
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.fileManagementService = fileManagementService;
        this.authorizedClientManager = authorizedClientManager;
        this.securityUtils = securityUtils;
        this.songService = songService;
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

        if (cachedFile.isPresent() && cachedFile.get().getMetadata() != null) {
            StoredFile sf = cachedFile.get();
            AudioFileMetadataDto dto = toAudioFileMetadataDto(sf);

            if (isPreviewUrlExpired(sf)) {
                String previewUrl = getFilePreviewUrl(fileId);
                sf.setPreviewUrl(previewUrl);
                fileRepository.save(sf);
            }

            return Optional.of(dto);
        }
        return Optional.empty();
    }

    @Transactional
    public void updateFileMetadata(String fileId, AudioMetadataExtractionDto dto) {
        UUID userId = securityUtils.getCurrentUserId();
        StoredFile sf = fileRepository.findByOwnerIdAndExternalId(userId, fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        applyMetadataToFile(sf, dto, userId);
         fileRepository.save(sf);
    }

    private void applyMetadataToFile(StoredFile sf, AudioMetadataExtractionDto dto, UUID userId) {
        ApplicationUser userRef = userRepository.getReferenceById(userId);

        StoredFileMetadata meta = sf.getMetadata();
        if (meta == null) {
            meta = new StoredFileMetadata();
            sf.setMetadata(meta);
        }

        meta.setTitle(dto.getTitle());
        meta.setAlbumCoverUrl(dto.getAlbumCoverUrl());
        meta.setGenres(dto.getGenres());
        meta.setYear(dto.getYear());
        meta.setDuration(dto.getDuration() != 0 ? dto.getDuration() : null);

        if (dto.getAlbum() != null && !dto.getAlbum().isBlank()) {
            Album album = albumRepository.findByNameAndUserId(dto.getAlbum(), userId)
                    .orElseGet(() -> {
                        Album a = new Album();
                        a.setName(dto.getAlbum());
                        a.setUser(userRef);
                        return albumRepository.save(a);
                    });
            meta.setAlbum(album);
        }

        if (dto.getArtistName() != null && !dto.getArtistName().isBlank()) {
            Artist artist = artistRepository.findByNameAndUserIdOrderByNameAsc(dto.getArtistName(), userId)
                    .orElseGet(() -> {
                        Artist a = new Artist(dto.getArtistName(), userRef);
                        return artistRepository.save(a);
                    });
            List<Artist> artists = new ArrayList<>();
            artists.add(artist);
            meta.setArtists(artists);
        }
    }

    @Transactional
    public Optional<FolderContentsDto> getFolderContentsFromCache(String folderId) {
        var folder = folderRepository.findByOwnerIdAndProviderAndExternalId(securityUtils.getCurrentUserId(), getProvider(), folderId);

        if (folder.isEmpty() || (folder.get().getFiles().isEmpty() && folder.get().getFolders().isEmpty())) {
            return Optional.empty();
        }

        List<FolderDto> folders = folder.get().getFolders().stream()
                .map(f -> new FolderDto(f.getName(), getProvider(), f.getPath(), f.getExternalId()))
                .sorted(Comparator.comparing(FolderDto::name))
                .collect(Collectors.toList());

        List<FileDto> files = folder.get().getFiles().stream()
                .map(entry -> new FileDto(
                        entry.getName(),
                        getProvider(),
                        entry.getExternalId(),
                        entry.getExternalId(),
                        entry.getMetadata() != null ? entry.getPreviewUrl() : null,
                        entry.getLastModified(),
                        entry.getMetadata() != null ? toAudioFileMetadataDto(entry) : null
                ))
                .sorted(Comparator.comparing(FileDto::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        return Optional.of(new FolderContentsDto(folders, files));
    }

    @Transactional
    protected FolderContentsDto enrichWithCachedMetadata(String folderId, FolderContentsDto contents) {
        UUID userId = securityUtils.getCurrentUserId();
        var storedFolder = folderRepository.findByOwnerIdAndProviderAndExternalId(userId, getProvider(), folderId);
        if (storedFolder.isEmpty()) {
            return contents;
        }

        Map<String, StoredFile> localFilesById = storedFolder.get().getFiles().stream()
                .collect(Collectors.toMap(StoredFile::getExternalId, f -> f));

        List<FileDto> enriched = contents.files().stream()
                .map(fileDto -> {
                    StoredFile local = localFilesById.get(fileDto.id());
                    if (local == null || local.getMetadata() == null) {
                        return fileDto;
                    }
                    return new FileDto(
                            fileDto.name(),
                            fileDto.provider(),
                            fileDto.path(),
                            fileDto.id(),
                            local.getPreviewUrl(),
                            fileDto.lastModified(),
                            toAudioFileMetadataDto(local)
                    );
                })
                .collect(Collectors.toList());

        return new FolderContentsDto(contents.folders(), enriched);
    }

    protected boolean isPreviewUrlExpired(StoredFile sf) {
        if (sf.getPreviewUrl() == null) {
            return true;
        }
        Duration age = Duration.between(sf.getLastModified().toInstant(), OffsetDateTime.now(ZoneOffset.UTC));
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

    protected StoredFolder getOrCreateFolder(UUID userId, String folderId) {
        return folderRepository.findByOwnerIdAndProviderAndExternalId(userId, getProvider(), folderId)
                .orElseGet(() -> {
                    var folder = new StoredFolder();
                    folder.setExternalId(folderId);
                    folder.setProvider(getProvider());
                    folder.setOwner(userRepository.getReferenceById(userId));
                    folder.setName(folderId.isEmpty() ? "root" : folderId);
                    folder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
                    return folderRepository.save(folder);
                });
    }

    @Transactional
    protected void updateFolderContents(String folderId, FolderContentsDto contents) {
        UUID userId = securityUtils.getCurrentUserId();
        ApplicationUser currentUser = userRepository.getReferenceById(userId);

        var parentFolder = getOrCreateFolder(userId, folderId);
        parentFolder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));

        Set<String> remoteFolderIds = contents.folders().stream()
                .map(FolderDto::id)
                .collect(Collectors.toSet());
        parentFolder.getFolders().removeIf(f -> !remoteFolderIds.contains(f.getExternalId()));

        Set<String> localFolderIds = parentFolder.getFolders().stream()
                .map(StoredFolder::getExternalId)
                .collect(Collectors.toSet());
        for (FolderDto folderDto : contents.folders()) {
            if (!localFolderIds.contains(folderDto.id())) {
                var folder = new StoredFolder();
                folder.setExternalId(folderDto.id());
                folder.setProvider(getProvider());
                folder.setOwner(currentUser);
                folder.setName(folderDto.name());
                folder.setPath(folderDto.path());
                folder.setParent(parentFolder);
                folder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
                parentFolder.getFolders().add(folder);
            } else {
                // TODO verify
                // Update path in case it changed (e.g. folder renamed on the provider side)
                parentFolder.getFolders().stream()
                        .filter(f -> f.getExternalId().equals(folderDto.id()))
                        .findFirst()
                        .ifPresent(f -> f.setPath(folderDto.path()));
            }
        }

        Set<String> remoteFileIds = contents.files().stream()
                .map(FileDto::id)
                .collect(Collectors.toSet());
        parentFolder.getFiles().removeIf(f -> !remoteFileIds.contains(f.getExternalId()));

        Set<String> localFileIds = parentFolder.getFiles().stream()
                .map(StoredFile::getExternalId)
                .collect(Collectors.toSet());
        for (FileDto fileDto : contents.files()) {
            if (!localFileIds.contains(fileDto.id())) {
                var file = new StoredFile();
                file.setExternalId(fileDto.id());
                file.setProvider(getProvider());
                file.setOwner(currentUser);
                file.setName(fileDto.name() != null ? fileDto.name() : fileDto.id());
                file.setFolder(parentFolder);
                file.setLastModified(fileDto.lastModified() != null ? fileDto.lastModified() : OffsetDateTime.now(ZoneOffset.UTC));
                file.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
                file.setType(FileType.AUDIO);
                // metadata is null on first pass; populated after getOrUpdateAudioMetadata
                parentFolder.getFiles().add(file);
            }
        }

        folderRepository.save(parentFolder);
    }

    protected AudioFileMetadataDto toAudioFileMetadataDto(StoredFile sf) {
        StoredFileMetadata meta = sf.getMetadata();
        if (meta == null) return null;
        String albumCoverUrl = fileManagementService.generateAccessUrlIfExpired(meta.getAlbumCoverUrl(), Duration.ofDays(7));
        return new AudioFileMetadataDto(
                meta.getTitle(),
                meta.getArtists().stream().map(Artist::getName).toList(),
                meta.getAlbum() != null ? meta.getAlbum().getName() : null,
                meta.getGenres(),
                albumCoverUrl,
                meta.getDuration(),
                sf.getPreviewUrl()
        );
    }

    @Transactional
    public void deleteAllFoldersAndFiles() {
        UUID userId = securityUtils.getCurrentUserId();
        fileRepository.deleteAllFilesAndFoldersForUserId(userId);
    }
}
