package com.cloudbeats.services;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.Playlist;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.dto.PlaylistDto;
import com.cloudbeats.dto.SongDto;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.FileRepository;
import com.cloudbeats.repositories.PlaylistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final FileRepository fileRepository;
    private final SongService songService;
    private final SecurityUtils securityUtils;
    private final ApplicationUserService applicationUserService;

    public PlaylistService(
            PlaylistRepository playlistRepository,
            FileRepository fileRepository,
            SongService songService,
            SecurityUtils securityUtils,
            ApplicationUserService applicationUserService
    ) {
        this.playlistRepository = playlistRepository;
        this.fileRepository = fileRepository;
        this.songService = songService;
        this.securityUtils = securityUtils;
        this.applicationUserService = applicationUserService;
    }

    public List<PlaylistDto> getAllPlaylists() {
        UUID ownerId = securityUtils.getCurrentUserId();
        return playlistRepository.findByOwnerId(ownerId).stream()
                .map(this::toPlaylistDto)
                .toList();
    }

    public PlaylistDto getPlaylist(Long id) {
        UUID ownerId = securityUtils.getCurrentUserId();
        Playlist playlist = playlistRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        return toPlaylistDto(playlist);
    }

    @Transactional
    public PlaylistDto createPlaylist(String name) {
        Playlist playlist = new Playlist();
        playlist.setName(name);
        ApplicationUser owner = applicationUserService.findApplicationUserById(securityUtils.getCurrentUserId());
        playlist.setOwner(owner);
        return toPlaylistDto(playlistRepository.save(playlist));
    }

    @Transactional
    public PlaylistDto updatePlaylist(Long id, String name) {
        UUID ownerId = securityUtils.getCurrentUserId();
        Playlist playlist = playlistRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        playlist.setName(name);
        return toPlaylistDto(playlistRepository.save(playlist));
    }

    @Transactional
    public void deletePlaylist(Long id) {
        UUID ownerId = securityUtils.getCurrentUserId();
        Playlist playlist = playlistRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        playlistRepository.delete(playlist);
    }

    @Transactional
    public PlaylistDto addSong(Long playlistId, Provider provider, String externalId) {
        UUID ownerId = securityUtils.getCurrentUserId();
        Playlist playlist = playlistRepository.findByIdAndOwnerId(playlistId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));

        StoredFile file = fileRepository.findByOwnerIdAndProviderAndExternalId(ownerId, provider, externalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found"));

        if (!playlist.getSongs().contains(file)) {
            playlist.getSongs().add(file);
            playlistRepository.save(playlist);
        }
        return toPlaylistDto(playlist);
    }

    @Transactional
    public PlaylistDto removeSong(Long playlistId, Provider provider, String externalId) {
        UUID ownerId = securityUtils.getCurrentUserId();
        Playlist playlist = playlistRepository.findByIdAndOwnerId(playlistId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));

        playlist.getSongs().removeIf(f ->
                f.getProvider() == provider && f.getExternalId().equals(externalId));
        playlistRepository.save(playlist);
        return toPlaylistDto(playlist);
    }

    private PlaylistDto toPlaylistDto(Playlist playlist) {
        List<SongDto> songs = playlist.getSongs().stream()
                .map(songService::toSongDto)
                .toList();
        return new PlaylistDto(playlist.getId(), playlist.getName(), songs);
    }
}
