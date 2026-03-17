package com.cloudbeats.dto;

import java.util.List;

public class AudioMetadataExtractionDto {
    private String title;
    private String artistName;
    private String album;
    private String albumCoverUrl;
    private List<String> genres;
    private Integer year;
    private double duration;

    public AudioMetadataExtractionDto(String title, String artistName, String album, String albumCoverUrl, List<String> genres, Integer year, double duration) {
        this.title = title;
        this.artistName = artistName;
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

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
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

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }
}

