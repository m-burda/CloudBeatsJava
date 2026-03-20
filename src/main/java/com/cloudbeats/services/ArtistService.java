package com.cloudbeats.services;

import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.repositories.ArtistRepository;
import com.cloudbeats.repositories.FileRepository;
import com.cloudbeats.dto.ArtistDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final FileRepository fileRepository;
    private final ApplicationUserService applicationUserService;
    private final FileManagementService fileManagementService;
    private final SecurityUtils securityUtils;

    public ArtistService(ArtistRepository artistRepository, FileRepository fileRepository, ApplicationUserService applicationUserService, FileManagementService fileManagementService, SecurityUtils securityUtils) {
        this.artistRepository = artistRepository;
        this.fileRepository = fileRepository;
        this.applicationUserService = applicationUserService;
        this.fileManagementService = fileManagementService;
        this.securityUtils = securityUtils;
    }

    public List<ArtistDto> getAllArtists() {
        UUID userId = securityUtils.getCurrentUserId();
        // TODO n+1
        return artistRepository.findByUserId(userId).stream()
                .map(artist -> {
                    var files = fileRepository.findByOwnerIdOrderByName(userId);
                    String albumCoverUrl = files.stream()
                            .filter(file -> file.getMetadata() != null
                                    && file.getMetadata().getArtists().stream()
                                    .anyMatch(a -> a.getName().equals(artist.getName())))
                            .findFirst()
                            .map(file -> fileManagementService.generateAccessUrlIfExpired(
                                    file.getMetadata().getAlbumCoverUrl(),
                                    Duration.ofDays(7)
                            ))
                            .orElse(null);

                    return new ArtistDto(artist.getName(), albumCoverUrl);
                })
                .sorted(Comparator.comparing(ArtistDto::getName))
                .toList();
    }
}
