package com.cloudbeats.dto;

import com.cloudbeats.db.entities.AudioFileMetadata;
import com.cloudbeats.models.Provider;

public record Song (
        String name,
        Provider provider,
        String path,
        String id,
        String previewUrl,
        String albumCoverUrl,
        AudioFileMetadata metadata
){}
