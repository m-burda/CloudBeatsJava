package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.StoredFolder;
import com.cloudbeats.models.Provider;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends CrudRepository<StoredFolder, UUID> {
    Optional<StoredFolder> findByOwnerIdAndProviderAndExternalId(UUID ownerId, Provider provider, String externalId);
    Optional<StoredFolder> findByProviderAndExternalId(Provider provider, String externalId);
    Optional<StoredFolder> findByOwnerIdAndProviderAndParentIsNull(UUID ownerId, Provider provider);
}

