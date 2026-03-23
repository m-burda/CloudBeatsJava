package com.cloudbeats.dto;

import java.util.List;

public class AudioMetadataExtractionDto {
    private String title;
    private List<String> artists;
    private String album;
    private String albumCoverUrl;
    private List<String> genres;
    private Integer year;
    private Integer duration;

    public AudioMetadataExtractionDto(String title, List<String> artists, String album, String albumCoverUrl, List<String> genres, Integer year, Integer duration) {
        this.title = title;
        this.artists = artists;
        this.album = album;
        this.albumCoverUrl = albumCoverUrl;
        this.genres = genres;
        this.year = year;
        this.duration = duration;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getArtists() {
        return artists;
    }

    public void setArtists(List<String> artists) {
        this.artists = artists;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getAlbumCoverUrl() {
        return albumCoverUrl;
    }

    public void setAlbumCoverUrl(String albumCoverUrl) {
        this.albumCoverUrl = albumCoverUrl;
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

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}

