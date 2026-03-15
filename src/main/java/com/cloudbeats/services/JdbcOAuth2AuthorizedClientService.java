package com.cloudbeats.services;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.models.Provider;
import com.cloudbeats.repositories.ApplicationUserRepository;
import com.cloudbeats.repositories.MediaStorageAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

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
        // You can implement this to load tokens back into Spring's context if needed
        return null;
    }

    @Override
    public void removeAuthorizedClient(String registrationId, String principalName) {
        // Logic for unlinking accounts
    }
}
