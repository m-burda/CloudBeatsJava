package com.cloudbeats.db.entities;

import com.cloudbeats.models.Provider;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stored_folders")
@IdClass(StoredFolderId.class) // Links the composite key
public class StoredFolder {

    @Id
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Id
    @Column(name = "external_id")
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private ApplicationUser owner;

    @Column(nullable = false)
    private String name;

    @Column
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "parent_provider", referencedColumnName = "provider"),
            @JoinColumn(name = "parent_external_id", referencedColumnName = "external_id")
    })
    private StoredFolder parent;

    @Column(name = "last_synced")
    private OffsetDateTime lastSynced;

    @Column(name = "last_modified")
    private OffsetDateTime lastModified;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void touchLastModified() {
        this.lastModified = OffsetDateTime.now(ZoneOffset.UTC);
    }

    // Navigation properties
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoredFolder> folders = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoredFile> files = new ArrayList<>();

    public List<StoredFolder> getFolders() {
        return folders;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public ApplicationUser getOwner() {
        return owner;
    }

    public StoredFolder getParent() {
        return parent;
    }

    public OffsetDateTime getLastSynced() {
        return lastSynced;
    }

    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public List<StoredFile> getFiles() {
        return files;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setOwner(ApplicationUser owner) {
        this.owner = owner;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setLastSynced(OffsetDateTime lastSynced) {
        this.lastSynced = lastSynced;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public void setParent(StoredFolder parent) {
        this.parent = parent;
    }
}
