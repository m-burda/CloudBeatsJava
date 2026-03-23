package com.cloudbeats.controllers;

import com.cloudbeats.dto.MediaStorageAccountDto;
import com.cloudbeats.models.Provider;
import com.cloudbeats.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final JdbcTemplate jdbcTemplate;
    private final SecurityUtils securityUtils;

    public UserController(JdbcTemplate jdbcTemplate, SecurityUtils securityUtils) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/profile")
    public ResponseEntity<List<MediaStorageAccountDto>> getProfile() {
        String sql = "SELECT client_registration_id FROM oauth2_authorized_client WHERE principal_name = ?";

        var providers = jdbcTemplate.queryForList(sql, String.class, securityUtils.getCurrentUserEmail());

        var linkedProviders = Stream.of(Provider.values()).map(provider -> {
            boolean isLinked = providers.stream().anyMatch(p -> p.equalsIgnoreCase(provider.name()));
            return new MediaStorageAccountDto(
                provider.name(),
                isLinked
            );
        }).toList();
        return ResponseEntity.ok(linkedProviders);
    }

    @GetMapping("/is-signed-in")
    public ResponseEntity<Void> isSignedIn() {
        try {
            securityUtils.getCurrentUserId();
        }
        catch (IllegalStateException e){
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok().build();
    }
}
