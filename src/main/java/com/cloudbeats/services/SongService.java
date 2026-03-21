package com.cloudbeats.services;

import com.cloudbeats.db.entities.Artist;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.db.entities.StoredFileMetadata;
import com.cloudbeats.dto.FileDto;
import com.cloudbeats.dto.SongDto;
import com.cloudbeats.models.Provider;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.repositories.FileRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class SongService {
    private final FileRepository fileRepository;
    private final FileManagementService fileManagementService;
    private final SecurityUtils securityUtils;

    public SongService(FileRepository fileRepository, FileManagementService fileManagementService, SecurityUtils securityUtils) {
        this.fileRepository = fileRepository;
        this.fileManagementService = fileManagementService;
        this.securityUtils = securityUtils;
    }

    public List<SongDto> getAllSongs() {
        return fileRepository.findByOwnerIdOrderByName(securityUtils.getCurrentUserId()).stream()
                .map(this::toSongDto).toList();
    }

    public List<SongDto> searchSongs(String query, String artist) {
        UUID ownerId = securityUtils.getCurrentUserId();

        List<StoredFile> results;
        if (!query.isBlank() && !artist.isBlank()) {
            results = fileRepository.fullTextSearchByArtist(ownerId, query, artist);
        } else if (!query.isBlank()) {
            results = fileRepository.fullTextSearch(ownerId, query);
        } else if (!artist.isBlank()) {
            results = fileRepository.findByArtist(ownerId, artist);
        } else {
            results = List.of();
        }
        return results.stream().map(this::toSongDto).toList();
    }

    public SongDto toSongDto(StoredFile file) {
        StoredFileMetadata meta = file.getMetadata();
        String albumCoverUrl = meta == null ? null :
                fileManagementService.getOrSetAlbumCoverUrl(file.getProvider(), meta.getAlbumCoverInternalUri(), Duration.ofDays(7));
        List<String> artists = meta == null ? null :
                meta.getArtists().stream().map(Artist::getName).toList();
        return new SongDto(
                meta != null && meta.getTitle() != null ? meta.getTitle() : file.getName(),
                artists,
                meta != null && meta.getAlbum() != null ? meta.getAlbum().getName() : null,
                meta != null ? meta.getDuration() : null,
                file.getProvider(),
                file.getExternalId(),
                file.getExternalId(),
                null,
                albumCoverUrl,
                file.getLastModified()
        );
    }

    public SongDto toSongDto(FileDto fileDto) {
        return new SongDto(
                fileDto.title() != null ? fileDto.title() : fileDto.name(),
                fileDto.artists(),
                fileDto.album(),
                fileDto.duration(),
                fileDto.provider(),
                fileDto.id(),
                fileDto.id(),
                fileDto.previewUrl(),
                null,
                fileDto.lastModified()
        );
    }
}
