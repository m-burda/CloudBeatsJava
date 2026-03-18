package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.dto.PlaylistDto;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.ApplicationUserService;
import com.cloudbeats.services.PlaylistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final ApplicationUserService userService;

    public PlaylistController(PlaylistService playlistService, ApplicationUserService userService) {
        this.playlistService = playlistService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<PlaylistDto>> getAllPlaylists() {
        return ResponseEntity.ok(playlistService.getAllPlaylists());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistDto> getPlaylist(@PathVariable Long id) {
        return ResponseEntity.ok(playlistService.getPlaylist(id));
    }

    @PostMapping
    public ResponseEntity<PlaylistDto> createPlaylist(@RequestBody CreatePlaylistRequest request) {
        PlaylistDto created = playlistService.createPlaylist(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlaylistDto> updatePlaylist(
            @PathVariable Long id,
            @RequestBody UpdatePlaylistRequest request
    ) {
        return ResponseEntity.ok(playlistService.updatePlaylist(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long id) {
        playlistService.deletePlaylist(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/songs")
    public ResponseEntity<PlaylistDto> addSong(
            @PathVariable Long id,
            @RequestBody SongReference request
    ) {
        return ResponseEntity.ok(playlistService.addSong(id, request.provider, request.externalId()));
    }

    @DeleteMapping("/{id}/songs")
    public ResponseEntity<PlaylistDto> removeSong(
            @PathVariable Long id,
            @RequestBody SongReference request
    ) {
        return ResponseEntity.ok(playlistService.removeSong(id, request.provider(), request.externalId()));
    }

    record CreatePlaylistRequest(String name) {}
    record UpdatePlaylistRequest(String name) {}
    record SongReference(Provider provider, String externalId) {}
}

