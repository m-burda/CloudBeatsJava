package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.db.entities.*;
import com.cloudbeats.dto.*;
import com.cloudbeats.repositories.*;
import com.cloudbeats.services.InMemoryCacheService;
import com.cloudbeats.services.NotificationService;
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
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public abstract class ExternalMediaStorageService {
    protected final Executor taskExecutor;
    private final ApplicationUserRepository userRepository;
    protected final FolderRepository folderRepository;
    protected final FileRepository fileRepository;
    protected final ArtistRepository artistRepository;
    protected final AlbumRepository albumRepository;
    protected final FileManagementService fileManagementService;
    protected final InMemoryCacheService cacheService;
    protected final OAuth2AuthorizedClientManager authorizedClientManager;
    protected final SecurityUtils securityUtils;
    protected NotificationService notificationService;

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
            Executor taskExecutor
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
        this.taskExecutor = taskExecutor;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    protected OAuth2AuthorizedClient getAuthorizedClient() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(getProvider().name())
                .principal(securityUtils.getAuthentication())
                .build();

        return authorizedClientManager.authorize(request);
    }

    public abstract Provider getProvider();

    public abstract FolderContentsDto listFiles(String folderId, boolean cached);
    protected abstract PreviewUrlResult fetchFilePreviewUrl(String fileId);
    /**
     * Returns the previewUrl immediately and fires an async task to download the file,
     * extract metadata, and push the result to the user via SSE.
     */
    public abstract String getOrUpdateMetadata(String fileId);

    protected void sendMetadataUpdate(SongDto song, String userId) {
        notificationService.sendMetadataUpdated(userId, song);
    }

    protected String getOrFetchPreviewUrl(String fileId) {
        String userId = securityUtils.getCurrentUserId().toString();
        String cached = cacheService.getPreviewUrl(userId, getProvider(), fileId);
        if (cached != null) {
            return cached;
        }
        PreviewUrlResult preview = fetchFilePreviewUrl(fileId);
        cacheService.setPreviewUrl(userId, getProvider(), fileId, preview.url(), preview.expiresIn());
        return preview.url();
    }

    @Transactional
    public void updateMetadata(UUID userId, String fileId, AudioMetadataExtractionDto dto) {
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

        List<Artist> artists = new ArrayList<>();

        if (dto.getArtists() != null && !dto.getArtists().isEmpty()) {
            List<Artist> artist = artistRepository.findAllByNameInAndUserId(dto.getArtists(), userId);
            artists.addAll(artist);
            meta.setArtists(artists);
        }
    }

    @Transactional
    public Optional<FolderContentsDto> getFolderContentsFromCache(String folderId) {
        UUID userId = securityUtils.getCurrentUserId();
        var folder = folderRepository.findByOwnerIdAndProviderAndExternalId(userId, getProvider(), folderId);

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
    protected FolderContentsDto enrichFolderContentsWithCachedMetadata(String folderId, FolderContentsDto contents) {
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
    public SongDto toSongDto(StoredFile file) {
        StoredFileMetadata meta = file.getMetadata();
        String albumCoverUrl = meta != null ? getOrSetAlbumCoverUrl(file.getOwner().getId().toString(), meta.getAlbumCoverInternalUri()) : null;
        List<String> artists = (meta != null && meta.getArtists() != null)
                ? meta.getArtists().stream().map(Artist::getName).toList()
                : List.of();
        String title = (meta != null && meta.getTitle() != null) ? meta.getTitle() : file.getName();
        String album = (meta != null && meta.getAlbum() != null) ? meta.getAlbum().getName() : null;
        Integer duration = meta != null ? meta.getDuration() : null;

        return new SongDto(
                title,
                artists,
                album,
                duration,
                file.getProvider(),
                file.getExternalId(),
                file.getExternalId(),
                albumCoverUrl,
                file.getLastModified()
        );
    }

    public String getOrSetAlbumCoverUrl(String userId, String internalUri){
        return fileManagementService.getOrSetAlbumCoverUrl(
                userId,
                getProvider(),
                internalUri
        );
    }

    protected SongDto toSongDtoWithPreview(StoredFile file) {
        SongDto base = toSongDto(file);
        String previewUrl = getOrFetchPreviewUrl(file.getExternalId());
        return new SongDto(
                base.title(),
                base.albumArtists(),
                base.album(),
                base.duration(),
                base.provider(),
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
