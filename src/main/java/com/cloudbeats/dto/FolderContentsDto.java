package com.cloudbeats.dto;

import java.util.List;

public record FolderContentsDto(
        List<FolderDto> folders,
        List<SongDto> files
) {}
