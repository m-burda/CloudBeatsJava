package com.cloudbeats.dto;

import com.cloudbeats.models.Provider;

import java.time.OffsetDateTime;
import java.util.List;

public record SongDto(
        String title,
        List<String> albumArtists,
        String album,
        Integer duration,
        Provider provider,
        String id,
        String previewUrl,
        String albumCoverUrl,
        OffsetDateTime lastModified
) {}
