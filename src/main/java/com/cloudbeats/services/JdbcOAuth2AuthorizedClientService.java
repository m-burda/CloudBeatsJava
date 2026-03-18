package com.cloudbeats.services;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.ApplicationUserRepository;
import com.cloudbeats.repositories.MediaStorageAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

@Service
public class JdbcOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private final MediaStorageAccountRepository mediaStorageAccountRepository;
    private final ApplicationUserRepository userRepository;

    public JdbcOAuth2AuthorizedClientService(MediaStorageAccountRepository mediaStorageAccountRepository, ApplicationUserRepository userRepository) {
        this.mediaStorageAccountRepository = mediaStorageAccountRepository;
        this.userRepository = userRepository;
    }
    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        String username = principal.getName();
        ApplicationUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String providerId = authorizedClient.getClientRegistration().getRegistrationId();
        Provider provider = Provider.valueOf(providerId.toLowerCase());

        MediaStorageAccount account = mediaStorageAccountRepository
                .findByUserIdAndProvider(user.getId(), provider)
                .orElseGet(() -> {
                    MediaStorageAccount newAcc = new MediaStorageAccount();
                    newAcc.setUser(user);
                    newAcc.setProvider(provider);
                    return newAcc;
                });

        account.setAccessToken(authorizedClient.getAccessToken().getTokenValue());
        account.setTokenExpiresAt(Date.from(authorizedClient.getAccessToken().getExpiresAt()));
        account.setTokenIssuedUtc(Date.from(authorizedClient.getAccessToken().getIssuedAt()));

        if (authorizedClient.getRefreshToken() != null) {
            account.setRefreshToken(authorizedClient.getRefreshToken().getTokenValue());
        }

        mediaStorageAccountRepository.save(account);
    }

    @Override
    public OAuth2AuthorizedClient loadAuthorizedClient(String registrationId, String principalName) {
        return null;
    }

    @Override
    public void removeAuthorizedClient(String registrationId, String principalName) {
        ApplicationUser user = userRepository.findByUsername(principalName)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Provider provider = Provider.valueOf(registrationId);

        var account = mediaStorageAccountRepository.findByUserIdAndProvider(user.getId(), provider).orElseThrow(
                () -> new RuntimeException("No linked account found for provider: " + provider)
        );

        mediaStorageAccountRepository.delete(account);
    }
}
