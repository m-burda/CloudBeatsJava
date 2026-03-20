package com.cloudbeats.db.entities;

import com.cloudbeats.models.FileType;
import com.cloudbeats.models.Provider;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stored_files")
@IdClass(StoredFileId.class)
public class StoredFile {

    @Id
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Id
    @Column(name = "external_id", length = 1024)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private ApplicationUser owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "preview_url")
    private String previewUrl;

    @OneToOne(cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    @JoinColumn(name = "metadata_id", referencedColumnName = "id")
    private StoredFileMetadata metadata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType type;

    @Column(name = "last_synced")
    private OffsetDateTime lastSynced;

    @Column(name = "last_modified")
    private OffsetDateTime lastModified;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "search_text", insertable = false, updatable = false)
    private String searchText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "folder_provider", referencedColumnName = "provider"),
            @JoinColumn(name = "folder_external_id", referencedColumnName = "external_id")
    })
    private StoredFolder folder;


    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public ApplicationUser getOwner() {
        return owner;
    }

    public void setOwner(ApplicationUser o) {
        this.owner = o;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public StoredFileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(StoredFileMetadata metadata) {
        this.metadata = metadata;
        if (metadata != null) metadata.setFile(this);
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(OffsetDateTime t) {
        this.lastModified = t;
    }

    public OffsetDateTime getLastSynced() {
        return lastSynced;
    }

    public void setLastSynced(OffsetDateTime t) {
        this.lastSynced = t;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public StoredFolder getFolder() {
        return folder;
    }

    public void setFolder(StoredFolder folder) {
        this.folder = folder;
    }
}
