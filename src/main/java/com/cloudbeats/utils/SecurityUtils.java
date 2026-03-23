package com.cloudbeats.utils;

import com.cloudbeats.db.entities.ApplicationUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public ApplicationUser getCurrentUser() {
        Authentication auth = getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ApplicationUser user)) {
            throw new IllegalStateException("No authenticated user found");
        }
        return user;
    };

    public UUID getCurrentUserId() {
        var user = getCurrentUser();
        return user.getId();
    }

    public String getCurrentUserEmail() {
        var user = getCurrentUser();
        return user.getUsername();
    }
}

