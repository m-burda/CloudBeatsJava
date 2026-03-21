package com.cloudbeats.dto;

import com.cloudbeats.models.Provider;

import java.time.OffsetDateTime;
import java.util.List;

public record FileDto (
    String id,
    String name,
    Provider provider,
    OffsetDateTime lastModified,
    String title,
    List<String> artists,
    String album,
    String albumCoverInternalUri,
    String previewUrl,
    Integer duration
){}
