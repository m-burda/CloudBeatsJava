package com.cloudbeats.services;

import com.cloudbeats.db.entities.Artist;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.dto.FileDto;
import com.cloudbeats.dto.SongDto;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.repositories.FileRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

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

    public List<SongDto> searchSongs(String query) {
        return fileRepository.fullTextSearch(securityUtils.getCurrentUserId(), query).stream()
                .map(this::toSongDto).toList();
    }

    public SongDto toSongDto(StoredFile file) {
        var metadata = file.getMetadataJson();
        if (metadata != null && metadata.getAlbumCoverUrl() != null) {
            metadata.setAlbumCoverUrl(
                    fileManagementService.generateAccessUrlIfExpired(metadata.getAlbumCoverUrl(), Duration.ofDays(7)));
        }
        String previewUrl = metadata == null ? null : metadata.getPreviewUrl();
        List<String> artists = metadata == null ? null :
                metadata.getAlbumArtists().stream()
                        .map(Artist::getName)
                        .toList();
        return new SongDto(
                file.getName(),
                artists,
                metadata != null ? metadata.getAlbum() : null,
                metadata != null ? metadata.getDuration() : null,
                file.getProvider(),
                file.getExternalId(),
                file.getExternalId(),
                previewUrl,
                metadata != null ? metadata.getAlbumCoverUrl() : null,
                file.getLastModified()
        );
    }

    // TODO duplication
    public SongDto toSongDto(FileDto fileDto) {
        var metadata = fileDto.metadata();
        if (metadata != null && metadata.getAlbumCoverUrl() != null) {
            metadata.setAlbumCoverUrl(
                    fileManagementService.generateAccessUrlIfExpired(metadata.getAlbumCoverUrl(), Duration.ofDays(7)));
        }
        String previewUrl = metadata == null ? null : metadata.getPreviewUrl();
        List<String> artists = metadata == null ? null :
                metadata.getAlbumArtists().stream()
                        .map(Artist::getName)
                        .toList();
        return new SongDto(
                fileDto.name(),
                artists,
                metadata != null ? metadata.getAlbum() : null,
                metadata != null ? metadata.getDuration() : null,
                fileDto.provider(),
                fileDto.path(),
                fileDto.id(),
                previewUrl,
                metadata != null ? metadata.getAlbumCoverUrl() : null,
                fileDto.lastModified()
        );
    }
}
