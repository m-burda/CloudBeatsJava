package com.cloudbeats.controllers;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.dto.MediaStorageAccountDto;
import com.cloudbeats.models.Provider;
import com.cloudbeats.services.ApplicationUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final ApplicationUserService userService;

    public UserController(ApplicationUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<List<MediaStorageAccountDto>> getProfile(@AuthenticationPrincipal UserDetails user) {
        ApplicationUser applicationUser = userService.findApplicationUserByUsername(user.getUsername());

        List<MediaStorageAccount> connectedProviders = applicationUser.getMediaStorageAccounts();
        List<MediaStorageAccountDto> result = Stream.of(Provider.values())
            .map(provider -> {
                boolean isLinked = connectedProviders.stream()
                    .anyMatch(account -> account.getProvider().equals(provider));
                return new MediaStorageAccountDto(provider.name(), isLinked);
            })
            .sorted(Comparator.comparing(MediaStorageAccountDto::isLinked).reversed()
                .thenComparing(MediaStorageAccountDto::getProvider))
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

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
