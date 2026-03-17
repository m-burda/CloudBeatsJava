package com.cloudbeats.services;

import com.cloudbeats.db.entities.AudioFileMetadata;
import com.cloudbeats.repositories.ArtistRepository;
import com.cloudbeats.repositories.FileRepository;
import com.cloudbeats.dto.ArtistDto;
import com.cloudbeats.db.entities.StoredFile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final FileRepository fileRepository;
    private final ApplicationUserService applicationUserService;
    private final FileManagementService fileManagementService;

    public ArtistService(ArtistRepository artistRepository, FileRepository fileRepository, ApplicationUserService applicationUserService, FileManagementService fileManagementService) {
        this.artistRepository = artistRepository;
        this.fileRepository = fileRepository;
        this.applicationUserService = applicationUserService;
        this.fileManagementService = fileManagementService;
    }

    public List<ArtistDto> getArtistsByUserId(String userId) {
        // TODO n+1
        var user = applicationUserService.findApplicationUserByUsername(userId);
        return artistRepository.findByUserId(user.getId()).stream()
                .map(artist -> {
                    // Find the first stored file with metadata containing this artist
                    var files = fileRepository.findByOwnerId(user.getId());
                    String albumCoverUrl = files.stream()
                            .filter(file -> file.getMetadataJson() != null
                                    && file.getMetadataJson().getAlbumArtists() != null
                                    && file.getMetadataJson().getAlbumArtists().stream()
                                    .anyMatch(a -> a.getName().equals(artist.getName())))
                            .findFirst()
                            .map(StoredFile::getMetadataJson)
                            .map(f -> fileManagementService.generateAccessUrlIfExpired(
                                    f.getAlbumCoverUrl(),
                                    Duration.ofDays(7)
                            ))
                            .orElse(null);

                    return new ArtistDto(artist.getName(), albumCoverUrl);
                })
                .toList();
    }
}
