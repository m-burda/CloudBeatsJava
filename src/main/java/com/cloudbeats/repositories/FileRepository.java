package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.models.Provider;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends CrudRepository<StoredFile, UUID> {
    Optional<StoredFile> findByOwnerIdAndExternalId(UUID ownerId, String externalId);
    Optional<StoredFile> findByOwnerIdAndProviderAndExternalId(UUID ownerId, Provider provider, String externalId);
    List<StoredFile> findByOwnerIdOrderByName(UUID ownerId);
}
