package com.cloudbeats.db.entities;

import com.cloudbeats.models.Provider;

import java.io.Serializable;

public class StoredFileId implements Serializable {
    private Provider provider;
    private String externalId;

    public StoredFileId(Provider provider, String externalId) {
        this.provider = provider;
        this.externalId = externalId;
    }

    public StoredFileId() {
    }
}
