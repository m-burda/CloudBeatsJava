package com.cloudbeats.services;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.repositories.ApplicationUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ApplicationUserService implements UserDetailsService {
    private final ApplicationUserRepository userRepository;

    public ApplicationUserService(ApplicationUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return findApplicationUserByUsername(username);
    }

    public ApplicationUser findApplicationUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        }

    public ApplicationUser findApplicationUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
