package com.cloudbeats.services;

import com.cloudbeats.db.entities.*;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.ApplicationUserRepository;
import com.cloudbeats.repositories.FileRepository;
import com.cloudbeats.repositories.FolderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final ApplicationUserRepository userRepository;

    private static final Duration DEFAULT_CACHE_DURATION = Duration.ofHours(1);

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository, ApplicationUserRepository userRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StoredFolder getFolder(UUID userId, Provider provider, String folderId) {
        if (folderId == null || folderId.isEmpty() || folderId.equals("/")) {
            return folderRepository.findByOwnerIdAndProviderAndParentIsNull(userId, provider).orElseThrow();
        }
        return folderRepository.findByOwnerIdAndProviderAndExternalId(userId, provider, folderId).orElseThrow();
    }
}
