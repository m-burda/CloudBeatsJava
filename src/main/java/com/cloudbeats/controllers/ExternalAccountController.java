package com.cloudbeats.controllers;

import com.cloudbeats.models.Provider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/external")
public class ExternalAccountController {
    @GetMapping("/{provider}")
    public String linkAccount(@PathVariable Provider provider) {
        // Implement logic to link external account based on the provider
        return "Linking account for provider: " + provider.name();
    }
}
