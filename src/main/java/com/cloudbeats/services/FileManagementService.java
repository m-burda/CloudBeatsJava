package com.cloudbeats.services;

import com.cloudbeats.models.Provider;

import java.nio.file.Path;
import java.time.Duration;

public interface FileManagementService {
    String writeData(byte[] data, Path path);
    String getOrSetAlbumCoverUrl(Provider provider, String internalUri, Duration duration);
}
