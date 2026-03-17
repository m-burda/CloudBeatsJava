package com.cloudbeats.services;

import com.cloudbeats.db.config.S3Config;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

@Service
@Profile("dev")
public class S3FileManagementService implements FileManagementService {
    private final S3Client client;
    private final S3Presigner presigner;

    public S3FileManagementService(S3Client s3Client) {
        this.client = s3Client;
        this.presigner = S3Presigner.builder().build();
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

            URI uri = new URI(
                    "s3",
                    null,
                    S3Config.BUCKET_NAME,
                    -1,
                    "/" + key,
                    null,
                    null
            );
            return uri.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save file to S3", e);
        }
    }

    @Override
    public String generateAccessUrlIfExpired(String internalUri, Duration duration) {
        if (internalUri == null || internalUri.isEmpty()) {
            return null;
        }

        // TODO check for expiration

        try {
            S3Utilities s3Utilities = S3Utilities.builder().region(S3Config.REGION).build();
            S3Uri parsedUri = s3Utilities.parseUri(URI.create(internalUri));
            Optional<String> key = parsedUri.key();

            if (key.isEmpty()) {
                return internalUri;
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(S3Config.BUCKET_NAME)
                    .key(key.get())
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate S3 pre-signed URL for: " + internalUri, e);
        }
    }
}

