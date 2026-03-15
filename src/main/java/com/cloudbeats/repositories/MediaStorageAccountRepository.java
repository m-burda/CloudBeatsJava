package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.ApplicationUser;
import com.cloudbeats.db.entities.MediaStorageAccount;
import com.cloudbeats.models.Provider;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaStorageAccountRepository extends CrudRepository<MediaStorageAccount, UUID> {
        Optional<MediaStorageAccount> findByUserIdAndProvider(UUID id, Provider provider);
}
