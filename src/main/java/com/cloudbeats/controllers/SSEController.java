package com.cloudbeats.controllers;

import com.cloudbeats.services.NotificationService;
import com.cloudbeats.utils.SecurityUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SSEController {
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;

    public SSEController(SecurityUtils securityUtils, NotificationService notificationService) {
        this.securityUtils = securityUtils;
        this.notificationService = notificationService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return notificationService.subscribe(securityUtils.getCurrentUserId());
    }
}
