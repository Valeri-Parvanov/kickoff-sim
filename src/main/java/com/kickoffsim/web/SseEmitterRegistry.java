package com.kickoffsim.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<UUID, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = createEmitter();
        List<SseEmitter> emitters = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    SseEmitter createEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    public void push(List<UUID> userIds, String message, String type) {
        if (userIds == null || userIds.isEmpty()) return;
        for (UUID userId : userIds) {
            List<SseEmitter> emitters = emittersByUser.get(userId);
            if (emitters == null || emitters.isEmpty()) continue;
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("toast")
                            .data(Map.of("message", message, "type", type)));
                } catch (IOException | IllegalStateException e) {
                    log.warn("Failed to push SSE event to user {}: {}", userId, e.getMessage());
                    remove(userId, emitter);
                }
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
