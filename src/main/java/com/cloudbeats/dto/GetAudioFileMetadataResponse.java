package com.cloudbeats.dto;

import java.util.List;

public record GetAudioFileMetadataResponse(
        String title,
        List<String> albumArtists,
        String album,
        List<String> genres,
        String albumCoverUrl,
        Double duration,
        String previewUrl
){
}
