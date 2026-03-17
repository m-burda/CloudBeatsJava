package com.cloudbeats.services;

import java.nio.file.Path;
import java.time.Duration;

public interface FileManagementService {
    public String writeData(byte[] data, Path path);
    String generateAccessUrlIfExpired(String internalUri, Duration duration);
}
