package com.cloudbeats.services;

import com.cloudbeats.models.Provider;
import com.cloudbeats.utils.SecurityUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService implements InMemoryCacheService{
    private final StringRedisTemplate redisTemplate;
    private final SecurityUtils securityUtils;

    public RedisCacheService(
        StringRedisTemplate redisTemplate,
        SecurityUtils securityUtils
    ) {
        this.redisTemplate = redisTemplate;
        this.securityUtils = securityUtils;
    }

    @Override
    public String getPreviewUrlKey(String userId, Provider provider, String key) {
        return String.join(
                ":",
                userId,
                provider.toString(),
                key
        );
    }

    @Override
    public String getPreviewUrl(String userId, Provider provider, String key) {
        String cacheKey = getPreviewUrlKey(userId, provider, key);
        return redisTemplate.opsForValue().get(cacheKey);
    }

    @Override
    public void setPreviewUrl(String userId, Provider provider, String key, String previewUrl, Duration expiresIn) {
        String cacheKey = getPreviewUrlKey(userId, provider, key);
        redisTemplate.opsForValue().set(cacheKey, previewUrl, expiresIn.getSeconds(), TimeUnit.SECONDS);
    }
}
