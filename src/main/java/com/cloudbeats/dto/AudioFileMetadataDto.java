package com.cloudbeats.dto;

import java.util.List;

public record AudioFileMetadataDto(
        String title,
        List<String> albumArtists,
        String album,
        List<String> genres,
        String albumCoverUrl,
        Integer duration,
        String previewUrl
){
}
