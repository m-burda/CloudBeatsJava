package com.cloudbeats.models;

import com.cloudbeats.db.entities.StoredFileMetadata;

import java.util.List;

public record FolderEntry (
    String previewUrl,
    FileType type,
    String name,
    Provider provider,
    String path,
    String id,
    StoredFileMetadata metadata,
    List<FolderEntry> folders,
    List<FolderEntry> files
){}
