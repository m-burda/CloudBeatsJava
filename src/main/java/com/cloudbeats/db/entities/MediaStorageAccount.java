package com.cloudbeats.db.entities;

import com.cloudbeats.models.Provider;
import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
public class MediaStorageAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String accountName;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private ApplicationUser user;

    private String accountUserId;
    @Column(columnDefinition = "TEXT")
    private String accessToken;
    private Date tokenExpiresAt;
    private Date tokenIssuedUtc;
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public ApplicationUser getUser() {
        return user;
    }

    public void setUser(ApplicationUser user) {
        this.user = user;
    }

    public String getAccountUserId() {
        return accountUserId;
    }

    public void setAccountUserId(String accountUserId) {
        this.accountUserId = accountUserId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Date getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Date tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public Date getTokenIssuedUtc() {
        return tokenIssuedUtc;
    }

    public void setTokenIssuedUtc(Date tokenIssuedUtc) {
        this.tokenIssuedUtc = tokenIssuedUtc;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
