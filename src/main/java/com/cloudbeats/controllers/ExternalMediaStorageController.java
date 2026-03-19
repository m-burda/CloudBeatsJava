package com.cloudbeats.controllers;

import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.factories.ExternalMediaStorageServiceFactory;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.ApplicationUserService;
import com.cloudbeats.services.externalMediaStorage.ExternalMediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/external")
public class ExternalMediaStorageController {
    private final ExternalMediaStorageServiceFactory storageFactory;

    public ExternalMediaStorageController(ExternalMediaStorageServiceFactory storageFactory) {
        this.storageFactory = storageFactory;
    }

    @GetMapping("/{provider}/files/list")
    public ResponseEntity<FolderContentsDto> listFiles(
        @PathVariable Provider provider,
        @RequestParam(defaultValue = "root") String id,
        @RequestParam(defaultValue = "true") boolean cached
    ) {
        ExternalMediaStorageService storageService = storageFactory.getService(provider);
        FolderContentsDto contents = storageService.listFiles(id, cached);

        return ResponseEntity.ok(contents);
    }

    private record GetMetadataFileRequest(
            String Path
    ){}

    @PostMapping("/{provider}/files")
    public ResponseEntity<AudioFileMetadataDto> getFileMetadata(
            @PathVariable Provider provider,
            @RequestBody GetMetadataFileRequest requestData
    ) {
        ExternalMediaStorageService storageService = storageFactory.getService(provider);
        AudioFileMetadataDto metadata = storageService.getOrUpdateAudioMetadata(requestData.Path);

        return ResponseEntity.ok(metadata);
    }

    @DeleteMapping("/files")
    public ResponseEntity<HttpStatus> deleteAllFoldersAndFiles() {
        ExternalMediaStorageService storageService = storageFactory.getService(Provider.dropbox);
        storageService.deleteAllFoldersAndFiles();
        return ResponseEntity.noContent().build();
    }


}
