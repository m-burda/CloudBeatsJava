package com.cloudbeats.db.entities;

import com.cloudbeats.models.Provider;

import java.io.Serializable;

public class StoredFolderId implements Serializable {
    private Provider provider;
    private String externalId;
}
