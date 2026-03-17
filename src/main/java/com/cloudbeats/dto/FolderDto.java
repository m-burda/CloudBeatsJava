package com.cloudbeats.dto;

import com.cloudbeats.models.Provider;

public record FolderDto(
        String name,
        Provider provider,
        String path,
        String id
) {}

