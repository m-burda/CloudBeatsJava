package com.cloudbeats.db.entities;

import com.cloudbeats.models.FileType;
import com.cloudbeats.models.Provider;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@IdClass(StoredFileId.class)
public class StoredFile {

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

    @Column(name = "preview_url")
    private String previewUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType type;

    @Column(name = "last_synced")
    private OffsetDateTime lastSynced;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public void setMetadataJson(AudioFileMetadata metadataJson) {
        this.metadataJson = metadataJson;
    }

    /**
     * JSON Mapping for Metadata
     * If using PostgreSQL, 'jsonb' is the preferred type.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private AudioFileMetadata metadataJson;

    /**
     * Mapping to the Composite Key Folder
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "folder_provider", referencedColumnName = "provider"),
            @JoinColumn(name = "folder_external_id", referencedColumnName = "external_id")
    })
    private StoredFolder folder;

    public AudioFileMetadata getMetadataJson() {
        return metadataJson;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public void setOwner(ApplicationUser owner) {
        this.owner = owner;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFolder(StoredFolder folder) {
        this.folder = folder;
    }

    public void setLastModified(OffsetDateTime lastModified) {
        this.lastSynced = lastModified;
    }

    public StoredFolder getFolder() {
        return folder;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public Provider getProvider() {
        return provider;
    }

    public ApplicationUser getOwner() {
        return owner;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }
}
