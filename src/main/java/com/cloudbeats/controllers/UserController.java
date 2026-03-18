package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.dto.MediaStorageAccountDto;
import com.cloudbeats.models.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
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

//    @GetMapping("/profile")
//    public ResponseEntity<List<MediaStorageAccountDto>> getProfile(@AuthenticationPrincipal UserDetails user) {
//        ApplicationUser applicationUser = userService.findApplicationUserByUsername(user.getUsername());
//
//        List<MediaStorageAccount> connectedProviders = applicationUser.getMediaStorageAccounts();
//        List<MediaStorageAccountDto> result = Stream.of(Provider.values())
//            .map(provider -> {
//                boolean isLinked = connectedProviders.stream()
//                    .anyMatch(account -> account.getProvider().equals(provider));
//                return new MediaStorageAccountDto(provider.name(), isLinked);
//            })
//            .sorted(Comparator.comparing(MediaStorageAccountDto::isLinked).reversed()
//                .thenComparing(MediaStorageAccountDto::getProvider))
//            .collect(Collectors.toList());
//
//        return ResponseEntity.ok(result);
//    }

    @PutMapping("/update")
    public String updateUserProfile(@RequestBody ApplicationUser user) {
        // Implement logic to update user profile using ApplicationUser entity
        return "User profile updated: " + user.getUsername();
    }

    @GetMapping("/is-signed-in")
    public ResponseEntity<Boolean> isSignedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return ResponseEntity.ok(true);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(false);
    }
}
