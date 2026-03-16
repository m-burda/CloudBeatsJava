package com.cloudbeats.services;

import com.cloudbeats.db.entities.AudioFileMetadata;

import java.io.File;

public interface AudioProcessingService {
    public AudioFileMetadata extractAudioMetadata(String originalFileName, File file);
}
