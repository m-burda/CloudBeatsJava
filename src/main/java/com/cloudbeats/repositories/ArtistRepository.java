package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.Artist;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtistRepository extends CrudRepository<Artist, String> {
}

