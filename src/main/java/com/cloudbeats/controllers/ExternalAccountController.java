package com.cloudbeats.controllers;

import com.cloudbeats.models.Provider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/external")
public class ExternalAccountController {
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    public ExternalAccountController(OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity unlinkAccount(
            @PathVariable Provider provider,
            @AuthenticationPrincipal UserDetails principal
    ) {
        oAuth2AuthorizedClientService.removeAuthorizedClient(provider.name(), principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
