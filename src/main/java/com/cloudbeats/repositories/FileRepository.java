package com.cloudbeats.repositories;

import com.cloudbeats.db.entities.StoredFile;
import com.cloudbeats.models.Provider;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends CrudRepository<StoredFile, UUID> {
    Optional<StoredFile> findByOwnerIdAndExternalId(UUID ownerId, String externalId);

    Optional<StoredFile> findByOwnerIdAndProviderAndExternalId(UUID ownerId, Provider provider, String externalId);

    List<StoredFile> findByOwnerIdOrderByName(UUID ownerId);

    @Query(value = """
    SELECT sf.* FROM stored_files sf
    WHERE sf.owner_id = :ownerId
      AND sf.search_text ILIKE %:query%
    ORDER BY
      similarity(:query, sf.search_text) DESC,
      sf.name ASC
    LIMIT 50
    """, nativeQuery = true)
    List<StoredFile> fullTextSearch(@Param("ownerId") UUID ownerId, @Param("query") String query);

    @Query(value = """
    SELECT sf.* FROM stored_files sf
    INNER JOIN stored_file_artists sfa ON sfa.file_external_id = sf.external_id AND sfa.file_provider = sf.provider
    INNER JOIN artists a ON a.id = sfa.artist_id
    WHERE sf.owner_id = :ownerId
      AND sf.search_text ILIKE %:query%
      AND lower(a.name) = lower(:artist)
    ORDER BY
      similarity(:query, sf.search_text) DESC,
      sf.name ASC
    LIMIT 50
    """, nativeQuery = true)
    List<StoredFile> fullTextSearchByArtist(@Param("ownerId") UUID ownerId, @Param("query") String query, @Param("artist") String artist);

    @Query(value = """
    SELECT sf.* FROM stored_files sf
    INNER JOIN stored_file_metadata sm ON sm.id = sf.metadata_id
    INNER JOIN stored_file_metadata_artists sfma ON sfma.metadata_id = sm.id
    INNER JOIN artists a ON a.id = sfma.artist_id
    WHERE sf.owner_id = :ownerId
      AND a.name = :artist
    ORDER BY sf.name ASC
    """, nativeQuery = true)
    List<StoredFile> findByArtist(@Param("ownerId") UUID ownerId, @Param("artist") String artist);

    @Modifying
    @Transactional
    @Query(value =
            """
            WITH
            deleted_links AS (
                DELETE FROM playlist_songs
                WHERE playlist_id IN (SELECT id FROM playlists WHERE owner_id = :userId)
                RETURNING *
            ),
            deleted_files AS (
                DELETE FROM stored_files
                WHERE owner_id = :userId
                RETURNING *
            )
            DELETE FROM stored_folders
            WHERE owner_id = :userId
            """, nativeQuery = true)
    void deleteAllFilesAndFoldersForUserId(@Param("userId") UUID userId);
}
