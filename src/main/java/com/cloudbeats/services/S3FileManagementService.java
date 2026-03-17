package com.cloudbeats.services;

import com.cloudbeats.db.config.S3Config;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

@Service
@Profile("dev")
public class S3FileManagementService implements FileManagementService {
    private final S3Client client;

    public S3FileManagementService(S3Config config) {
        this.client = config.s3Client();
    }

    public String writeData(byte[] data, Path path) {
        if (data == null || data.length == 0) return null;

        try {
            String key = path.toString();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3Config.BUCKET_NAME)
                    .key(key)
                    .build();

            client.putObject(putObjectRequest, RequestBody.fromBytes(data));

            return String.format("s3://%s/%s", S3Config.BUCKET_NAME, key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save file to S3", e);
        }
    }
}

