package com.cloudbeats.services;

import com.cloudbeats.db.entities.Artist;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.dto.Song;
import com.cloudbeats.repositories.FileRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class SongService {
    private final FileRepository fileRepository;
    private final FileManagementService fileManagementService;

    public SongService(FileRepository fileRepository, FileManagementService fileManagementService) {
        this.fileRepository = fileRepository;
        this.fileManagementService = fileManagementService;
    }

    public List<Song> getAllSongs(UUID userId) {
        return fileRepository.findByOwnerIdOrderByName(userId).stream()
                .map(this::toSongDto).toList();
    }

    private Song toSongDto(StoredFile file) {
        var metadata = file.getMetadataJson();

        String albumCoverUrl = null;
        if(metadata != null && metadata.getAlbumCoverUrl() != null) {
            metadata.setAlbumCoverUrl(fileManagementService.generateAccessUrlIfExpired(metadata.getAlbumCoverUrl(), Duration.ofDays(7)));
        }
        String previewUrl = metadata == null ? null : metadata.getPreviewUrl();
        List<String> artists = metadata == null ? null : metadata.getAlbumArtists().stream().map(Artist::getName).toList();
        return new Song(
                file.getName(),
                artists,
                file.getProvider(),
                file.getExternalId(),
                file.getExternalId(),
                previewUrl,
                albumCoverUrl,
                metadata
        );
    }
}

