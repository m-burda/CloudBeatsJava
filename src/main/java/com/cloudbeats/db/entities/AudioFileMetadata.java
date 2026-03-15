package com.cloudbeats.db.entities;

import com.cloudbeats.models.AudioCodec;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.util.List;

@Entity
public class AudioFileMetadata extends FileMetadata {
    private String title;
    @ElementCollection
    private List<String> albumArtists;
    private String albumCoverUrl;
    @ElementCollection
    private List<String> performers;
    private String album;
    @ElementCollection
    private List<String> genres;
    private Integer year;

    @Transient
    private List<AudioCodec> audioCodecs;

    private double duration;

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAlbumArtists() {
        return albumArtists;
    }

    public void setAlbumArtists(List<String> albumArtists) {
        this.albumArtists = albumArtists;
    }

    public String getAlbumCoverUrl() {
        return albumCoverUrl;
    }

    public void setAlbumCoverUrl(String albumCoverUrl) {
        this.albumCoverUrl = albumCoverUrl;
    }

    public List<String> getPerformers() {
        return performers;
    }

    public void setPerformers(List<String> performers) {
        this.performers = performers;
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

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }
}
