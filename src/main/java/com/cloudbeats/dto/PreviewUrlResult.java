package com.cloudbeats.dto;

import java.time.Duration;

public record PreviewUrlResult(String url, Duration expiresIn) {
}

