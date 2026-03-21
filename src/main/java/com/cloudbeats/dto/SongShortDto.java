package com.cloudbeats.dto;

import com.cloudbeats.models.Provider;

import java.time.OffsetDateTime;
import java.util.List;

public record SongShortDto(
        String title,
        List<String> albumArtists,
        String album,
        Integer duration,
        Provider provider,
        String id,
        String albumCoverUrl,
        OffsetDateTime lastModified
){}
