package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.Album;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlbumRepository extends CrudRepository<Album, UUID> {
    Optional<Album> findByNameAndUserId(String name, UUID userId);
}

