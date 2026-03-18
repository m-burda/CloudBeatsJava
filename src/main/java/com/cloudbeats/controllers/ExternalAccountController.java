package com.cloudbeats.controllers;

import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.MediaStorageAccountRepository;
import com.cloudbeats.services.ApplicationUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@RestController
@RequestMapping("/api/account/external")
public class ExternalAccountController {
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public ExternalAccountController(OAuth2AuthorizedClientService oAuth2AuthorizedClientService, OAuth2AuthorizedClientManager authorizedClientManager) {
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.authorizedClientManager = authorizedClientManager;
    }

    @GetMapping("/callback/{provider}")
    public void handleCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication principal) throws IOException {

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(provider)
                .principal(principal)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();
        this.authorizedClientManager.authorize(authorizeRequest);

        String redirectUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/files/")
                .path(provider)
                .build().toUriString();


        response.sendRedirect(redirectUrl);
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
