package com.cloudbeats.controllers;

import com.cloudbeats.dto.SongDto;
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
    public ResponseEntity<List<SongDto>> getAllSongs(
            @AuthenticationPrincipal UserDetails principal
    ) {
        List<SongDto> songs = songService.getAllSongs();
        return ResponseEntity.ok(songs);
    }

    @GetMapping("/search")
    public ResponseEntity<List<SongDto>> searchSongs(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String artist
    ) {
        List<SongDto> songs = songService.searchSongs(q, artist);
        return ResponseEntity.ok(songs);
    }
}
