package com.kickoffsim.service.impl;

import com.kickoffsim.exception.RoundRecapGenerationException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class OllamaChatModelClient implements RecapChatModel {

    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public OllamaChatModelClient(
            @Value("${kickoffsim.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${kickoffsim.ollama.model:gemma3:4b}") String model,
            @Value("${kickoffsim.ollama.timeout-seconds:12}") int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String complete(String system, String user) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> call(system, user));
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RoundRecapGenerationException("The AI provider was interrupted.");
        } catch (Exception exception) {
            throw new RoundRecapGenerationException("The AI provider could not be reached.");
        } finally {
            executor.shutdownNow();
        }
    }

    private String call(String system, String user) {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(0.4)
                        .numCtx(8192)
                        .numPredict(320)
                        .keepAlive("30m")
                        .build())
                .build();
        return chatModel.call(new Prompt(new SystemMessage(system), new UserMessage(user)))
                .getResult().getOutput().getText();
    }
}
