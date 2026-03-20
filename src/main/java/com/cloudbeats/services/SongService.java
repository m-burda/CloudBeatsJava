package com.cloudbeats.services;

import com.cloudbeats.db.entities.Artist;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.db.entities.StoredFileMetadata;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FileDto;
import com.cloudbeats.dto.SongDto;
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
        String previewUrl = meta == null ? null : file.getPreviewUrl();
        List<String> artists = meta == null ? null :
                meta.getArtists().stream().map(Artist::getName).toList();
        String albumCoverUrl = meta == null ? null :
                fileManagementService.generateAccessUrlIfExpired(meta.getAlbumCoverUrl(), Duration.ofDays(7));
        return new SongDto(
                file.getName(),
                artists,
                meta != null && meta.getAlbum() != null ? meta.getAlbum().getName() : null,
                meta != null ? meta.getDuration() : null,
                file.getProvider(),
                file.getExternalId(),
                file.getExternalId(),
                previewUrl,
                albumCoverUrl,
                file.getLastModified()
        );
    }

    public SongDto toSongDto(FileDto fileDto) {
        AudioFileMetadataDto meta = fileDto.metadata();
        return new SongDto(
                fileDto.name(),
                meta != null ? meta.albumArtists() : null,
                meta != null ? meta.album() : null,
                meta != null ? meta.duration() : null,
                fileDto.provider(),
                fileDto.path(),
                fileDto.id(),
                meta != null ? fileDto.previewUrl() : null,
                meta != null ? meta.albumCoverUrl() : null,
                fileDto.lastModified()
        );
    }
}
