package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.dto.AudioFileMetadataDto;
import com.cloudbeats.factories.ExternalMediaStorageServiceFactory;
import com.cloudbeats.models.FolderEntry;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.ApplicationUserService;
import com.cloudbeats.services.externalMediaStorage.ExternalMediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/account/external")
public class ExternalMediaStorageController {
    private final ExternalMediaStorageServiceFactory storageFactory;
    private final ApplicationUserService userService;

    public ExternalMediaStorageController(ExternalMediaStorageServiceFactory storageFactory, ApplicationUserService userService) {
        this.storageFactory = storageFactory;
        this.userService = userService;
    }

    @GetMapping("/{provider}/files/list")
    public ResponseEntity<List<FolderEntry>> listFiles(
        @PathVariable Provider provider,
        @RequestParam(defaultValue = "") String path,
        @AuthenticationPrincipal UserDetails principal
    ) {
        ApplicationUser user = userService.findApplicationUserByUsername(principal.getUsername());

        MediaStorageAccount externalAccount = user.getMediaStorageAccounts().stream()
                .filter(acc -> acc.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider not linked"));

        ExternalMediaStorageService storageService = storageFactory.getService(provider);

        List<FolderEntry> files = storageService.listFiles(
                user.getId(),
                externalAccount.getAccountUserId(),
                path
        );

        return ResponseEntity.ok(files);
    }

    private record GetMetadataFileRequest(
            String Path
    ){}

    @PostMapping("/{provider}/files")
    public ResponseEntity<AudioFileMetadataDto> getFileMetadata(
            @PathVariable Provider provider,
            @RequestBody GetMetadataFileRequest requestData,
            @AuthenticationPrincipal UserDetails principal
    ) {
        ApplicationUser user = userService.findApplicationUserByUsername(principal.getUsername());

        ExternalMediaStorageService storageService = storageFactory.getService(provider);
        AudioFileMetadataDto metadata = storageService.getOrUpdateAudioMetadata(user.getId(), requestData.Path);

        return ResponseEntity.ok(metadata);
    }


}
