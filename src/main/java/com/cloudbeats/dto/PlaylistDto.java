package com.cloudbeats.dto;

import java.util.List;

public record PlaylistDto(
        Long id,
        String name,
        List<SongDto> songs
) {}

