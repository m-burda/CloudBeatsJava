package com.cloudbeats.controllers;

import com.cloudbeats.dto.Song;
import com.cloudbeats.services.SongService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class TracksController {
    private final SongService songService;

    public TracksController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public ResponseEntity<List<Song>> getAllSongs(
            @AuthenticationPrincipal UserDetails principal
    ) {
        List<Song> songs = songService.getAllSongs();
        return ResponseEntity.ok(songs);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Song>> searchSongs(
            @RequestParam String q
    ) {
        List<Song> songs = songService.searchSongs(q);
        return ResponseEntity.ok(songs);
    }
}
