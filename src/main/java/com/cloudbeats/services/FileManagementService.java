package com.cloudbeats.services;

import java.nio.file.Path;

public interface FileManagementService {
    public String writeData(byte[] data, Path path);
}
