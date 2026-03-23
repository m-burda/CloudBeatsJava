package com.cloudbeats.services;

import com.cloudbeats.config.MinIOConfig;
import com.cloudbeats.models.Provider;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

@Service
@Primary
@Profile({"local", "docker"})
public class MinIOFileManagementService implements FileManagementService {
    private final MinioClient client;
    private final String bucketName;
    private final InMemoryCacheService cacheService;

    public MinIOFileManagementService(
            MinIOConfig config,
            MinioClient client,
            InMemoryCacheService cacheService
    ) {
        this.client = client;
        this.bucketName = config.getBucketName();
        this.cacheService = cacheService;
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
    public String getOrSetAlbumCoverUrl(String userId, Provider provider, String internalUri) {
        if (internalUri == null) {
            return null;
        }

        String cached = cacheService.getPreviewUrl(userId, provider, internalUri);
        if (cached != null) {
            return cached;
        }

        try {
            String url = client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(internalUri)
                            .expiry((int) ALBUM_COVER_URL_EXPIRES_IN.toSeconds())
                            .build()
            );
            cacheService.setPreviewUrl(userId, provider, internalUri, url, ALBUM_COVER_URL_EXPIRES_IN);
            return url;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL from MinIO", e);
        }
    }
}

