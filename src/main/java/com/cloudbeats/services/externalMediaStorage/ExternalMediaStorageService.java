package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.db.entities.*;
import com.cloudbeats.dto.*;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.InMemoryCacheService;
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
    protected final InMemoryCacheService cacheService;
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
            InMemoryCacheService cacheService,
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
        this.cacheService = cacheService;
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
    protected abstract PreviewUrlResult fetchFilePreviewUrl(String fileId);
    public abstract void updateAudioMetadata(String fileId);
    public abstract SongDto getOrUpdateMetadata(String fileId);

    protected String getOrFetchPreviewUrl(String fileId) {
        String cached = cacheService.getPreviewUrl(getProvider(), fileId);
        if (cached != null) {
            return cached;
        }
        PreviewUrlResult preview = fetchFilePreviewUrl(fileId);
        cacheService.setPreviewUrl(getProvider(), fileId, preview.url(), preview.expiresIn());
        return preview.url();
    }

    protected Optional<SongDto> getMetadataFromCache(String fileId) {
        UUID userId = securityUtils.getCurrentUserId();
        Optional<StoredFile> cachedFile = fileRepository.findByOwnerIdAndExternalId(userId, fileId);
        if (cachedFile.isPresent() && cachedFile.get().getMetadata() != null) {
            return Optional.of(toSongDtoWithPreview(cachedFile.get()));
        }
        return Optional.empty();
    }

    @Transactional
    public void updateMetadata(String fileId, AudioMetadataExtractionDto dto) {
        UUID userId = securityUtils.getCurrentUserId();
        StoredFile sf = fileRepository.findByOwnerIdAndExternalId(userId, fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        addOrUpdateMetadata(sf, dto, userId);
        fileRepository.save(sf);
    }

    private void addOrUpdateMetadata(StoredFile sf, AudioMetadataExtractionDto dto, UUID userId) {
        ApplicationUser userRef = userRepository.getReferenceById(userId);

        StoredFileMetadata meta = sf.getMetadata();
        if (meta == null) {
            meta = new StoredFileMetadata();
            sf.setMetadata(meta);
        }

        meta.setTitle(dto.getTitle());
        meta.setAlbumCoverInternalUri(dto.getAlbumCoverUrl());
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

        List<SongDto> files = folder.get().getFiles().stream()
                .map(this::toSongDto)
                .sorted(Comparator.comparing(SongDto::title, Comparator.nullsLast(Comparator.naturalOrder())))
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

        List<SongDto> enriched = contents.files().stream()
                .map(song -> {
                    StoredFile local = localFilesById.get(song.id());
                    if (local == null || local.getMetadata() == null) {
                        return song;
                    }
                    return toSongDto(local);
                })
                .collect(Collectors.toList());

        return new FolderContentsDto(contents.folders(), enriched);
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
                .map(SongDto::id)
                .collect(Collectors.toSet());
        parentFolder.getFiles().removeIf(f -> !remoteFileIds.contains(f.getExternalId()));

        Set<String> localFileIds = parentFolder.getFiles().stream()
                .map(StoredFile::getExternalId)
                .collect(Collectors.toSet());
        for (SongDto dto : contents.files()) {
            if (!localFileIds.contains(dto.id())) {
                var sf = new StoredFile();
                sf.setExternalId(dto.id());
                sf.setProvider(getProvider());
                sf.setOwner(currentUser);
                sf.setName(dto.title() != null ? dto.title() : dto.id());
                sf.setFolder(parentFolder);
                sf.setLastModified(dto.lastModified() != null ? dto.lastModified() : OffsetDateTime.now(ZoneOffset.UTC));
                sf.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
                sf.setType(FileType.AUDIO);
                parentFolder.getFiles().add(sf);
            }
        }

        folderRepository.save(parentFolder);
    }

    /**
     * Converts a StoredFile to a SongDto with a resolved albumCoverUrl.
     * Includes a previewUrl only if one is already cached — does NOT fetch a new one.
     * Use for listFiles.
     */
    protected SongDto toSongDto(StoredFile file) {
        StoredFileMetadata meta = file.getMetadata();
        String albumCoverUrl = (meta != null && meta.getAlbumCoverInternalUri() != null)
                ? fileManagementService.getOrSetAlbumCoverUrl(file.getProvider(), meta.getAlbumCoverInternalUri(), Duration.ofDays(7))
                : null;
        List<String> artists = (meta != null && meta.getArtists() != null)
                ? meta.getArtists().stream().map(Artist::getName).toList()
                : List.of();
        String title = (meta != null && meta.getTitle() != null) ? meta.getTitle() : file.getName();
        String album = (meta != null && meta.getAlbum() != null) ? meta.getAlbum().getName() : null;
        Integer duration = meta != null ? meta.getDuration() : null;
        String previewUrl = cacheService.getPreviewUrl(getProvider(), file.getExternalId());

        return new SongDto(
                title,
                artists,
                album,
                duration,
                file.getProvider(),
                file.getExternalId(),
                file.getExternalId(),
                previewUrl,
                albumCoverUrl,
                file.getLastModified()
        );
    }

    /**
     * Converts a StoredFile to a SongDto with both a resolved albumCoverUrl and a fetched/cached previewUrl.
     * Use for getFileMetadata.
     */
    protected SongDto toSongDtoWithPreview(StoredFile file) {
        SongDto base = toSongDto(file);
        String previewUrl = getOrFetchPreviewUrl(file.getExternalId());
        return new SongDto(
                base.title(),
                base.albumArtists(),
                base.album(),
                base.duration(),
                base.provider(),
                base.path(),
                base.id(),
                previewUrl,
                base.albumCoverUrl(),
                base.lastModified()
        );
    }

    @Transactional
    public void deleteAllFoldersAndFiles() {
        UUID userId = securityUtils.getCurrentUserId();
        fileRepository.deleteAllFilesAndFoldersForUserId(userId);
    }
}
