package com.cloudbeats.services;

import com.cloudbeats.models.Provider;

import java.time.Duration;

public interface InMemoryCacheService {
    String getPreviewUrl(String userId, Provider provider, String fileId);
    String getPreviewUrlKey(String userId, Provider provider, String key);
    void setPreviewUrl(String userId, Provider provider, String key, String previewUrl, Duration expiresIn);
}
