package com.cloudbeats.models;

import com.cloudbeats.db.entities.AudioFileMetadata;

import java.util.List;

public record FolderEntry (
    String previewUrl,
    FileType type,
    String name,
    String path,
    String id,
    AudioFileMetadata metadata,
    List<FolderEntry> folders,
    List<FolderEntry> files
){}
