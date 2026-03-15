package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.StoredFile;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends CrudRepository<StoredFile, UUID> {
    Optional<StoredFile> findByOwnerIdAndExternalId(UUID ownerId, String externalId);
}
