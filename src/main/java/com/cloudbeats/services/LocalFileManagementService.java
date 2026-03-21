package com.cloudbeats.services;

import com.cloudbeats.models.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Service
@Profile("!dev")
public class LocalFileManagementService implements FileManagementService {
    private final Path storagePath = Paths.get(System.getProperty("user.dir")).resolve("files");

    private Path resolvePath(String directory) {
        return storagePath.resolve(directory).normalize();
    }

    public String writeData(byte[] data, Path path) {
        if (data == null || data.length == 0) return null;

        try {
            Path filePath = resolvePath(path.toString());

            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            if (!Files.exists(filePath)) {
                Files.write(filePath, data);
            }

            return filePath.getFileName().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save artwork", e);
        }
    }

    @Override
    public String getOrSetAlbumCoverUrl(Provider provider, String internalUri, Duration duration) {
        return internalUri;
    }
}
