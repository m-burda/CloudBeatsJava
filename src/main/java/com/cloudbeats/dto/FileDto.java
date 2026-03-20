package com.cloudbeats.dto;

import com.cloudbeats.models.Provider;

import java.time.OffsetDateTime;

/**
 * Internal DTO used within the service layer to carry file data including raw metadata.
 * Convert to {@link SongDto} before returning to the frontend.
 */
public record FileDto(
        String name,
        Provider provider,
        String path,
        String id,
        String previewUrl,
        OffsetDateTime lastModified,
        AudioFileMetadataDto metadata
) {}
