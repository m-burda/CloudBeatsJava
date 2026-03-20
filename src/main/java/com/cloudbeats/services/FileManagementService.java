package com.cloudbeats.services;

import java.nio.file.Path;
import java.time.Duration;

public interface FileManagementService {
    String writeData(byte[] data, Path path);
    String generateAccessUrlIfExpired(String internalUri, Duration duration);
}
