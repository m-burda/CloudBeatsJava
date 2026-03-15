package com.cloudbeats.dto;

public class MediaStorageAccountDto {

    private String provider;
    private boolean isLinked;

    public MediaStorageAccountDto(String provider, boolean isLinked) {
        this.provider = provider;
        this.isLinked = isLinked;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isLinked() {
        return isLinked;
    }

    public void setLinked(boolean linked) {
        isLinked = linked;
    }
}
