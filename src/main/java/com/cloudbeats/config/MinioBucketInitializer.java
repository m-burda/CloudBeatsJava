package com.cloudbeats.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MinioBucketInitializer implements CommandLineRunner {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioBucketInitializer(
            MinioClient minioClient,
            MinIOConfig config
    ) {
        this.minioClient = minioClient;
        this.bucketName = config.getBucketName();
    }

    @Override
    public void run(String... args) {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                System.out.printf("Created MinIO bucket: %s", bucketName);
            }
        } catch (Exception e) {
            System.out.printf("Failed to check/create MinIO bucket: %s", e);
        }
    }
}
