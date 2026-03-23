package com.cloudbeats.services;

import com.cloudbeats.models.Provider;

import java.nio.file.Path;
import java.time.Duration;

public interface FileManagementService {
    static Duration ALBUM_COVER_URL_EXPIRES_IN = Duration.ofDays(7);
    String writeData(byte[] data, Path path);
    String getOrSetAlbumCoverUrl(String userId, Provider provider, String internalUri);
}
