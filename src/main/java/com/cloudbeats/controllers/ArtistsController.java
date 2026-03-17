package com.cloudbeats.controllers;

import com.cloudbeats.dto.ArtistDto;
import com.cloudbeats.services.ArtistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/artists")
@RestController
public class ArtistsController {

    private final ArtistService artistService;

    public ArtistsController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @GetMapping
    public List<ArtistDto> getAllArtists(
            @AuthenticationPrincipal UserDetails principal
    ) {
        return artistService.getAllArtists(principal.getUsername());
    }
}
