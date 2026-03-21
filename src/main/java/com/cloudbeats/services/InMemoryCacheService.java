package com.cloudbeats.services;

import com.cloudbeats.models.Provider;

import java.time.Duration;

public interface InMemoryCacheService {
    String getPreviewUrl(Provider provider, String fileId);
    String getPreviewUrlKey(Provider provider, String key);
    void setPreviewUrl(Provider provider, String key, String previewUrl, Duration expiresIn);
}
