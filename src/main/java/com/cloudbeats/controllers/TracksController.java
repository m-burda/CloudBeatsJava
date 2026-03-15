package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.Track;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracks")
public class TracksController {

    @GetMapping("/all")
    public String getAllTracks() {
        // Implement logic to retrieve all tracks using Track entity
        return "List of all tracks";
    }

    @PostMapping("/add")
    public String addTrack(@RequestBody Track track) {
        // Implement logic to add a new track using Track entity
        return "Track added: " + track.getTitle();
    }
}
