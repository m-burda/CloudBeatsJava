package com.cloudbeats.services;

import com.cloudbeats.dto.SongDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    public SseEmitter subscribe(UUID userId) {
        String key = userId.toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.put(key, emitter);
        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> emitters.remove(key));
        emitter.onError(e -> emitters.remove(key));

        try {
            emitter.send(SseEmitter.event().name("connection").data(Map.of("status", "connected")));
        } catch (IOException e) {
            emitters.remove(key);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void send(String userId, String eventName, Object data){
        try {
            SseEmitter emitter = emitters.get(userId);
            emitter.send(SseEmitter.event().name(eventName).data(data, APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE to user {}, removing emitter", userId, e);
            emitters.remove(userId);
        }
    }

    public void sendError(String userId, String errorMessage) {
        send(userId, "error", Map.of("message", errorMessage));
    }

    public void sendMetadataUpdated(String userId, SongDto trackData) {
        send(userId, "metadata-updated", trackData);
    }
}

