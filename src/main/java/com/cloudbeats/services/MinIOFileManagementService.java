package com.cloudbeats.services;

import com.cloudbeats.config.MinIOConfig;
import com.cloudbeats.models.Provider;
import com.cloudbeats.utils.SecurityUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;

@Service
@Primary
@Profile({"local", "docker"})
public class MinIOFileManagementService implements FileManagementService {
    private final MinioClient client;
    private final String bucketName;
    private final InMemoryCacheService cacheService;
    private final SecurityUtils securityUtils;

    public MinIOFileManagementService(
            MinIOConfig config,
            MinioClient client,
            InMemoryCacheService cacheService, SecurityUtils securityUtils
    ) {
        this.client = client;
        this.bucketName = config.getBucketName();
        this.cacheService = cacheService;
        this.securityUtils = securityUtils;
    }

    @Override
    public String writeData(byte[] data, Path path) {
        if (data == null || data.length == 0) return null;

        try {
            String objectName = path.toString();

            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .build()
            );

            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    @Override
    public String getOrSetAlbumCoverUrl(Provider provider, String internalUri, Duration expiresIn) {
        if (internalUri == null) {
            return null;
        }

        String cached = cacheService.getPreviewUrl(provider, internalUri);
        if (cached != null) {
            return cached;
        }

        try {
            String url = client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(internalUri)
                            .expiry((int) expiresIn.toSeconds())
                            .build()
            );
            cacheService.setPreviewUrl(provider, internalUri, url, expiresIn);
            return url;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL from MinIO", e);
        }
    }
}

