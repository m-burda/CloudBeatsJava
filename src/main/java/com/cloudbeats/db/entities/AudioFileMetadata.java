package com.cloudbeats.db.entities;

import com.cloudbeats.models.AudioCodec;
import jakarta.persistence.*;
import java.util.List;

@Entity
public class AudioFileMetadata extends FileMetadata {
    private String title;
    @ManyToMany
    private List<Artist> albumArtists;
    @Deprecated
    private String albumCoverUrl;
    private String album;
    @ElementCollection
    private List<String> genres;
    private Integer year;

    @Transient
    private List<AudioCodec> audioCodecs;

    private Integer duration;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Artist> getAlbumArtists() {
        return albumArtists != null ? albumArtists : List.of();
    }

    public void setAlbumArtists(List<Artist> albumArtists) {
        this.albumArtists = albumArtists;
    }

    public String getAlbumCoverUrl() {
        return albumCoverUrl;
    }

    public void setAlbumCoverUrl(String albumCoverUrl) {
        this.albumCoverUrl = albumCoverUrl;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public List<AudioCodec> getAudioCodecs() {
        return audioCodecs;
    }

    public void setAudioCodecs(List<AudioCodec> audioCodecs) {
        this.audioCodecs = audioCodecs;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
