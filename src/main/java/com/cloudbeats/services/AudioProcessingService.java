package com.cloudbeats.services;

import com.cloudbeats.dto.AudioMetadataExtractionDto;

import java.io.File;

public interface AudioProcessingService {
    AudioMetadataExtractionDto extractAudioMetadata(String originalFileName, File file);
}
