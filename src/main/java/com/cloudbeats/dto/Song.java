package com.cloudbeats.dto;

import com.cloudbeats.db.entities.AudioFileMetadata;
import com.cloudbeats.models.Provider;

import java.util.List;

public record Song (
        String name,
        List<String> albumArtists,
        Provider provider,
        String path,
        String id,
        String previewUrl,
        String albumCoverUrl,
        AudioFileMetadata metadata
){}
