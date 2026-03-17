package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.Playlist;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistRepository extends CrudRepository<Playlist, Long> {
    List<Playlist> findByOwnerId(UUID ownerId);
    Optional<Playlist> findByIdAndOwnerId(Long id, UUID ownerId);
}

