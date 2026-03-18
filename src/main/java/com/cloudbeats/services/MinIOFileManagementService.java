package com.cloudbeats.services;

import com.cloudbeats.config.MinIOConfig;
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

    public MinIOFileManagementService(
        MinIOConfig config,
        MinioClient client
    ) {
        this.client = client;
        this.bucketName = config.getBucketName();
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
    public String generateAccessUrlIfExpired(String internalUri, Duration duration) {
        if (internalUri == null) {
            return null;
        }

        try {
            int validitySeconds = (int) Math.min(duration.toSeconds(), 604800);
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(internalUri)
                            .expiry(validitySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL from MinIO", e);
        }
    }
}

