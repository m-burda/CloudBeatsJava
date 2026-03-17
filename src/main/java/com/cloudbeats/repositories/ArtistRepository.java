package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.Artist;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtistRepository extends CrudRepository<Artist, String> {
        List<Artist> findByUserId(UUID userId);
        Optional<Artist> findByNameAndUserIdOrderByNameAsc(String name, UUID userId);
}

