package com.cloudbeats.controllers;

import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.dto.SongDto;
import com.cloudbeats.factories.ExternalMediaStorageServiceFactory;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.SongService;
import com.cloudbeats.services.externalMediaStorage.ExternalMediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/external")
public class ExternalMediaStorageController {
    private final ExternalMediaStorageServiceFactory storageFactory;
    private final SongService songService;

    public ExternalMediaStorageController(ExternalMediaStorageServiceFactory storageFactory, SongService songService) {
        this.storageFactory = storageFactory;
        this.songService = songService;
    }

    @GetMapping("/{provider}/files/list")
    public ResponseEntity<FolderContentsDto> listFiles(
        @PathVariable Provider provider,
        @RequestParam(defaultValue = "root") String id,
        @RequestParam(defaultValue = "true") boolean cached
    ) {
        ExternalMediaStorageService storageService = storageFactory.getService(provider);
        var contents = storageService.listFiles(id, cached);
        return ResponseEntity.ok(contents);
    }

    @GetMapping("/{provider}/files/{fileId}/metadata")
    public ResponseEntity<SongDto> getFileMetadata(
            @PathVariable Provider provider,
            @PathVariable String fileId
    ) {
        ExternalMediaStorageService storageService = storageFactory.getService(provider);
        SongDto metadata = storageService.getOrUpdateMetadata(fileId);
        return ResponseEntity.ok(metadata);
    }

    @DeleteMapping("/files")
    public ResponseEntity<HttpStatus> deleteAllFoldersAndFiles() {
        ExternalMediaStorageService storageService = storageFactory.getService(Provider.dropbox);
        storageService.deleteAllFoldersAndFiles();
        return ResponseEntity.noContent().build();
    }
}


