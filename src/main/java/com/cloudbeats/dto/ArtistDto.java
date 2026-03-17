package com.cloudbeats.dto;

public class ArtistDto {

    private String name;
    private String albumCoverUrl;

    public ArtistDto(String name, String albumCoverUrl) {
        this.name = name;
        this.albumCoverUrl = albumCoverUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlbumCoverUrl() {
        return albumCoverUrl;
    }

    public void setAlbumCoverUrl(String albumCoverUrl) {
        this.albumCoverUrl = albumCoverUrl;
    }
}
