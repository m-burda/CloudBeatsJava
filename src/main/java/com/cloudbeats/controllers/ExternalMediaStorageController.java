package com.cloudbeats.controllers;

import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.dto.FolderContentsDto;
import com.cloudbeats.utils.SecurityUtils;
import com.cloudbeats.factories.ExternalMediaStorageServiceFactory;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.ApplicationUserService;
import com.cloudbeats.services.externalMediaStorage.ExternalMediaStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/external")
public class ExternalMediaStorageController {
    private final ExternalMediaStorageServiceFactory storageFactory;
    private final ApplicationUserService userService;
    private final SecurityUtils securityUtils;


    public ExternalMediaStorageController(ExternalMediaStorageServiceFactory storageFactory, ApplicationUserService userService, SecurityUtils securityUtils) {
        this.storageFactory = storageFactory;
        this.userService = userService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/{provider}/files/list")
    public ResponseEntity<FolderContentsDto> listFiles(
        @PathVariable Provider provider,
        @RequestParam(defaultValue = "") String path
    ) {
        ExternalMediaStorageService storageService = storageFactory.getService(provider);
        FolderContentsDto contents = storageService.listFiles(path);

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


}
