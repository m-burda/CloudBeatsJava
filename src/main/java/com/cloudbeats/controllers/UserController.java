package com.cloudbeats.controllers;

import com.cloudbeats.dto.MediaStorageAccountDto;
import com.cloudbeats.models.Provider;
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

    public UserController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/profile")
    public ResponseEntity<List<MediaStorageAccountDto>> getProfile(@AuthenticationPrincipal UserDetails user) {
        String sql = "SELECT client_registration_id FROM oauth2_authorized_client WHERE principal_name = ?";

        var providers = jdbcTemplate.queryForList(sql, String.class, user.getUsername());

        var linkedProviders = Stream.of(Provider.values()).map(provider -> {
            boolean isLinked = providers.stream().anyMatch(p -> p.equalsIgnoreCase(provider.name()));
            return new MediaStorageAccountDto(
                provider.name(),
                isLinked
            );
        }).toList();
        return ResponseEntity.ok(linkedProviders);
    }
}
