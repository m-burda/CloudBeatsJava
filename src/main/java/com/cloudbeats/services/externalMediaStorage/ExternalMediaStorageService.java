package com.cloudbeats.services.externalMediaStorage;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.AudioFileMetadata;
import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.db.entities.StoredFolder;
import com.cloudbeats.models.FileType;
import com.cloudbeats.models.FolderEntry;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.ApplicationUserRepository;
import com.cloudbeats.repositories.FileRepository;
import com.cloudbeats.repositories.FolderRepository;
import jakarta.transaction.Transactional;
import org.apache.tika.Tika;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class ExternalMediaStorageService {
    private final ApplicationUserRepository userRepository;
    protected final FolderRepository folderRepository;
    protected final FileRepository fileRepository;
    protected static final Duration PREVIEW_URL_EXPIRE_DURATION = Duration.ofMinutes(10);

    protected ExternalMediaStorageService(ApplicationUserRepository userRepository, FolderRepository folderRepository, FileRepository fileRepository) {
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    public abstract Provider getProvider();
    public abstract List<FolderEntry> listFiles(UUID userId, String externalUserId, String folderId);
    public abstract AudioFileMetadata getOrUpdateAudioMetadata(UUID userId, String fileId);
    protected abstract String getFilePreviewUrl(UUID userId, String fileId);

    @Transactional
    public void updateFileMetadata(UUID userId, String fileId, AudioFileMetadata metadata) {
        StoredFile storedFile = fileRepository.findByOwnerIdAndExternalId(userId, fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        storedFile.setMetadataJson(metadata);
        fileRepository.save(storedFile);
    }

    @Transactional
    public List<FolderEntry> getFolderContentsFromCache(UUID userId, String folderId) {
        var folder = folderRepository.findByProviderAndExternalId(getProvider(), folderId);

        if (folder.isEmpty()) {
            return List.of();
        }

        var entries = folder.get().getFolders().stream().map(f ->
                new FolderEntry(
                        "",
                        FileType.FOLDER,
                        f.getName(),
                        f.getExternalId(),
                        f.getExternalId(),
                        null,
                        List.of(),
                        List.of()
                )).collect(Collectors.toCollection(ArrayList::new));

        folder.get().getFiles().forEach(entry -> {
            var metadata = entry.getMetadataJson();

            entries.add(new FolderEntry(
                    metadata != null ? metadata.getPreviewUrl() : "",
                    FileType.AUDIO,
                    entry.getName(),
                    entry.getExternalId(),
                    entry.getExternalId(),
                    metadata,
                    List.of(),
                    List.of()
            ));
        });

        return entries.stream().sorted(Comparator.comparing(FolderEntry::type)
                        .thenComparing(FolderEntry::name))
                .collect(Collectors.toList());
    }

    protected boolean isPreviewUrlExpired(AudioFileMetadata metadata) {
        if (metadata == null || metadata.getPreviewUrl() == null || metadata.getLastModified() == null) {
            return true;
        }

        Duration age = Duration.between(metadata.getLastModified().toInstant(), OffsetDateTime.now(ZoneOffset.UTC));
        return age.compareTo(PREVIEW_URL_EXPIRE_DURATION) > 0;
    }

    protected String getExtensionFromMime(File file) {
        Tika tika = new Tika();
        try {
            String mimeType = tika.detect(file);
            return switch (mimeType) {
                case "audio/mpeg" -> ".mp3";
                case "audio/x-flac", "audio/flac" -> ".flac";
                case "audio/mp4", "audio/x-m4a" -> ".m4a";
                case "audio/ogg", "application/ogg" -> ".ogg";
                case "audio/x-wav", "audio/wav" -> ".wav";
                default -> ".mp3"; // Default fallback
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    protected void updateFolderContents(UUID userId, String folderId, List<FolderEntry> entries) {
        // Get the current user
        ApplicationUser currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Get or create the parent folder
        String normalizedFolderId = folderId.equals("/") ? "" : folderId;
        var parentFolder = folderRepository.findByOwnerIdAndProviderAndExternalId(
                userId, getProvider(), normalizedFolderId
        ).orElseGet(() -> {
            var folder = new StoredFolder();
            folder.setExternalId(normalizedFolderId);
            folder.setProvider(getProvider());
            folder.setOwner(currentUser);
            folder.setName(normalizedFolderId.isEmpty() ? "root" : normalizedFolderId);
            folder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
            return folder;
        });

        // Update last synced time
        parentFolder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));

        // Clear existing subfolders and files
        parentFolder.getFolders().clear();
        parentFolder.getFiles().clear();

        // Save folder and file entries
        for (FolderEntry entry : entries) {
            if (entry.type() == FileType.FOLDER) {
                var folder = new StoredFolder();
                folder.setExternalId(entry.path());
                folder.setProvider(Provider.dropbox);
                folder.setOwner(currentUser);
                folder.setName(entry.name());
                folder.setParent(parentFolder);
                folder.setLastSynced(OffsetDateTime.now(ZoneOffset.UTC));
                parentFolder.getFolders().add(folder);
            } else if (entry.type() == FileType.AUDIO) {
                var file = new StoredFile();
                file.setExternalId(entry.path());
                file.setProvider(Provider.dropbox);
                file.setOwner(currentUser);
                file.setName(entry.name());
                file.setFolder(parentFolder);
                file.setLastModified(OffsetDateTime.now(ZoneOffset.UTC));
                file.setType(FileType.AUDIO);
                file.setMetadataJson(entry.metadata());
                parentFolder.getFiles().add(file);
            }
        }

        folderRepository.save(parentFolder);
    }
}
