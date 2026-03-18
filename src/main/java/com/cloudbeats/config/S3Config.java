package com.cloudbeats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {
    public final static String BUCKET_NAME = "cloudbeats-local";
    public static Region REGION;

    public S3Config(@Value("${aws.s3.region:eu-central-1}") Region region) {
        REGION = region;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
