package com.cloudbeats.db.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stored_file_metadata")
public class StoredFileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(mappedBy = "metadata", cascade = CascadeType.ALL, orphanRemoval = true)
    private StoredFile file;

    @Column
    private String title;

    @Column(name = "album_cover_url", length=1024)
    private String albumCoverUrlInternal;

    @Column
    private Integer duration;

    @Column
    private Integer year;

    @ElementCollection
    @CollectionTable(name = "stored_file_metadata_genres", joinColumns = @JoinColumn(name = "metadata_id"))
    @Column(name = "genre")
    private List<String> genres = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToMany
    @JoinTable(
            name = "stored_file_metadata_artists",
            joinColumns = @JoinColumn(name = "metadata_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private List<Artist> artists = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public StoredFile getFile() {
        return file;
    }

    public void setFile(StoredFile file) {
        this.file = file;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbumCoverInternalUri() {
        return albumCoverUrlInternal;
    }

    public void setAlbumCoverInternalUri(String url) {
        this.albumCoverUrlInternal = url;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres != null ? genres : new ArrayList<>();
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public List<Artist> getArtists() {
        return artists != null ? artists : new ArrayList<>();
    }

    public void setArtists(List<Artist> artists) {
        this.artists = artists != null ? artists : new ArrayList<>();
    }
}

