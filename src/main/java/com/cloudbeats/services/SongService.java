package com.cloudbeats.services;

import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.dto.SongDto;
import com.cloudbeats.factories.ExternalMediaStorageServiceFactory;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.repositories.FileRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SongService {
    private final FileRepository fileRepository;
    private final SecurityUtils securityUtils;
    private final ExternalMediaStorageServiceFactory externalMediaStorageServiceFactory;

    public SongService(
            FileRepository fileRepository,
            SecurityUtils securityUtils,
            ExternalMediaStorageServiceFactory externalMediaStorageServiceFactory
    ) {
        this.fileRepository = fileRepository;
        this.securityUtils = securityUtils;
        this.externalMediaStorageServiceFactory = externalMediaStorageServiceFactory;
    }

    public List<SongDto> getAllSongs() {
        return fileRepository.findByOwnerIdOrderByName(securityUtils.getCurrentUserId()).stream()
                .map(file -> {
                    var storageService = externalMediaStorageServiceFactory.getService(file.getProvider());
                    return storageService.toSongDto(file);
                }).toList();
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
        return results.stream().map(file -> {
            var storageService = externalMediaStorageServiceFactory.getService(file.getProvider());
            return storageService.toSongDto(file);
        }).toList();
    }
}
