package com.kickoffsim.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private static final long HEARTBEAT_MS = 20_000L;

    private static final int DISPATCH_THREADS = 4;

    private static final int QUEUE_CAPACITY = 1000;

    private final Map<UUID, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    private final Executor dispatcher;

    public SseEmitterRegistry() {
        this(newDispatcher());
    }

    SseEmitterRegistry(Executor dispatcher) {
        this.dispatcher = dispatcher;
    }

    private static Executor newDispatcher() {
        return new ThreadPoolExecutor(
                1, DISPATCH_THREADS, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "sse-dispatch");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = createEmitter();
        List<SseEmitter> emitters = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
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
                dispatcher.execute(() -> send(userId, emitter, SseEmitter.event()
                        .name("toast")
                        .data(Map.of("message", message, "type", type))));
            }
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        dispatchToAll(() -> SseEmitter.event().comment("keepalive"));
    }

    public void broadcastAll(String eventName) {
        dispatchToAll(() -> SseEmitter.event().name(eventName).data("1"));
    }

    private void dispatchToAll(Supplier<SseEmitter.SseEventBuilder> eventSupplier) {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                dispatcher.execute(() -> send(userId, emitter, eventSupplier.get()));
            }
        });
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            log.debug("Dropping disconnected SSE emitter for user {}: {}", userId, e.getMessage());
            remove(userId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
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
