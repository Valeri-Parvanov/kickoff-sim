package com.kickoffsim.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void register_returnsEmitter() {
        assertThat(registry.register(UUID.randomUUID())).isNotNull();
    }

    @Test
    void register_sameUserTwice_returnsDistinctEmitters() {
        UUID userId = UUID.randomUUID();
        SseEmitter first = registry.register(userId);
        SseEmitter second = registry.register(userId);

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void push_nullUserIds_doesNotThrow() {
        assertThatCode(() -> registry.push(null, "hi", "GOAL")).doesNotThrowAnyException();
    }

    @Test
    void push_emptyUserIds_doesNotThrow() {
        assertThatCode(() -> registry.push(List.of(), "hi", "GOAL")).doesNotThrowAnyException();
    }

    @Test
    void push_unknownUser_doesNotThrow() {
        assertThatCode(() -> registry.push(List.of(UUID.randomUUID()), "hi", "GOAL"))
                .doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void push_userHasEmptyEmitterList_isNoOp() throws Exception {
        // Simulates the narrow race window in register() between the list being
        // created and the emitter actually being added to it.
        UUID userId = UUID.randomUUID();
        Field field = SseEmitterRegistry.class.getDeclaredField("emittersByUser");
        field.setAccessible(true);
        Map<UUID, List<SseEmitter>> map = (Map<UUID, List<SseEmitter>>) field.get(registry);
        map.put(userId, new CopyOnWriteArrayList<>());

        assertThatCode(() -> registry.push(List.of(userId), "hi", "GOAL")).doesNotThrowAnyException();
    }

    @Test
    void push_registeredUser_sendsEvent() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);
        spyRegistry.push(List.of(userId), "hi", "GOAL");

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void push_multipleEmittersSameUser_sendsToBoth() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitterA = mock(SseEmitter.class);
        SseEmitter mockEmitterB = mock(SseEmitter.class);
        doReturn(mockEmitterA, mockEmitterB).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);
        spyRegistry.register(userId);
        spyRegistry.push(List.of(userId), "hi", "GOAL");

        verify(mockEmitterA).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockEmitterB).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void push_sendThrowsIOException_removesEmitterWithoutPropagating() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();
        doThrow(new IOException("broken pipe")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);

        assertThatCode(() -> spyRegistry.push(List.of(userId), "hi", "GOAL")).doesNotThrowAnyException();

        // emitter removed after failure - a second push shouldn't try to send again
        spyRegistry.push(List.of(userId), "hi again", "GOAL");
        verify(mockEmitter, org.mockito.Mockito.times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void push_sendThrowsIllegalStateException_removesEmitterWithoutPropagating() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();
        doThrow(new IllegalStateException("already complete")).when(mockEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);

        assertThatCode(() -> spyRegistry.push(List.of(userId), "hi", "GOAL")).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void onCompletion_removesEmitter() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockEmitter).onCompletion(captor.capture());
        captor.getValue().run();

        spyRegistry.push(List.of(userId), "hi", "GOAL");
        verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onCompletion_calledTwice_secondCallIsNoOp() {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockEmitter).onCompletion(captor.capture());
        captor.getValue().run();

        assertThatCode(() -> captor.getValue().run()).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void onTimeout_removesEmitter() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockEmitter).onTimeout(captor.capture());
        captor.getValue().run();

        spyRegistry.push(List.of(userId), "hi", "GOAL");
        verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onError_removesEmitter() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doReturn(mockEmitter).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);

        ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockEmitter).onError(captor.capture());
        captor.getValue().accept(new RuntimeException("boom"));

        spyRegistry.push(List.of(userId), "hi", "GOAL");
        verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void onCompletion_multipleEmittersSameUser_removesOnlyThatOne() throws IOException {
        SseEmitterRegistry spyRegistry = spy(new SseEmitterRegistry());
        SseEmitter mockEmitterA = mock(SseEmitter.class);
        SseEmitter mockEmitterB = mock(SseEmitter.class);
        doReturn(mockEmitterA, mockEmitterB).when(spyRegistry).createEmitter();

        UUID userId = UUID.randomUUID();
        spyRegistry.register(userId);
        spyRegistry.register(userId);

        ArgumentCaptor<Runnable> captorA = ArgumentCaptor.forClass(Runnable.class);
        verify(mockEmitterA).onCompletion(captorA.capture());
        captorA.getValue().run();

        spyRegistry.push(List.of(userId), "hi", "GOAL");
        verify(mockEmitterA, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockEmitterB).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void createEmitter_returnsRealSseEmitter() {
        assertThat(registry.createEmitter()).isNotNull();
    }
}
