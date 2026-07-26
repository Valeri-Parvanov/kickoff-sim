package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OllamaRoundRecapClientTest {

    @Test
    void generate_buildsFactOnlyLanguageSpecificPromptAndReturnsTrimmedContent() {
        AtomicReference<String> system = new AtomicReference<>();
        AtomicReference<String> user = new AtomicReference<>();
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((systemPrompt, userPrompt) -> {
            system.set(systemPrompt);
            user.set(userPrompt);
            return "  Original recap  ";
        });

        String result = client.generate(data("bg", "Bulgarian"));

        assertThat(result).isEqualTo("Original recap");
        assertThat(system.get())
                .contains("directly in Bulgarian")
                .contains("Do not translate")
                .contains("Every claim must be verifiable")
                .contains("Never invent statistics");
        assertThat(user.get())
                .contains("League: Test League")
                .contains("Round: 3")
                .contains("Output locale: bg")
                .contains("Alpha (Sofia) 2:1 Beta")
                .contains("Goal: Alpha, Alex, minute 12")
                .contains("1. Alpha | P 3 W 2 D 1 L 0 GF 5 GA 2 GD 3 PTS 7");
    }

    @Test
    void generate_emptyResponseThrowsControlledException() {
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((system, user) -> " ");

        assertThatThrownBy(() -> client.generate(data("en", "English")))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generate_nullResponseThrowsControlledException() {
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((system, user) -> null);

        assertThatThrownBy(() -> client.generate(data("en", "English")))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generate_gatewayFailureThrowsControlledException() {
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((system, user) -> {
            throw new IllegalStateException("remote failure");
        });

        assertThatThrownBy(() -> client.generate(data("de", "German")))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("could not generate")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void userPromptIncludesAllMatchAndStandingFields() {
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((system, user) -> "ok");

        String prompt = client.userPrompt(data("de", "German"));

        assertThat(prompt)
                .contains("Goal: Alpha, Alex, minute 12")
                .contains("D 1")
                .contains("L 0")
                .contains("GA 2");
    }

    @Test
    void seasonPromptUsesSeasonScopeWithoutFakeRoundNumber() {
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((system, user) -> "ok");
        RoundRecapPromptData season = new RoundRecapPromptData(
                "Test League", 0, "en", "English",
                data("en", "English").matches(),
                data("en", "English").standings());

        assertThat(client.systemPrompt(season))
                .contains("season overview")
                .doesNotContain("completed round");
        assertThat(client.userPrompt(season))
                .contains("Scope: Season to date")
                .doesNotContain("Round: 0");
    }

    @Test
    void userPromptCalculatesVerifiedLeaderAndKeyResults() {
        OllamaRoundRecapClient client = new OllamaRoundRecapClient((system, user) -> "ok");
        RoundRecapPromptData promptData = new RoundRecapPromptData(
                "Test League", 4, "en", "English",
                List.of(
                        new RoundRecapMatchData("Alpha", "Beta", 5, 0, List.of()),
                        new RoundRecapMatchData("Gamma", "Delta", 4, 3, List.of()),
                        new RoundRecapMatchData("Epsilon", "Zeta", 3, 3, List.of())),
                List.of(
                        new RoundRecapStandingData(2, "Beta", 4, 2, 0, 2, 5, 5, 0, 6),
                        new RoundRecapStandingData(1, "Alpha", 4, 3, 1, 0, 10, 2, 8, 10)));

        assertThat(client.userPrompt(promptData))
                .contains("Current leader: Alpha with 10 points")
                .contains("Largest winning margin: Alpha 5:0 Beta")
                .contains("Highest-scoring match: Gamma 4:3 Delta")
                .contains("Closest result: Epsilon 3:3 Zeta");
    }

    @Test
    void productionConstructorCallsLocalOllamaWithConfiguredModel() {
        OllamaApi.Builder apiBuilder = mock(OllamaApi.Builder.class);
        OllamaApi ollamaApi = mock(OllamaApi.class);
        OllamaChatModel.Builder modelBuilder = mock(OllamaChatModel.Builder.class);
        OllamaChatModel chatModel = mock(OllamaChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage output = mock(AssistantMessage.class);
        when(apiBuilder.baseUrl("http://localhost:11434")).thenReturn(apiBuilder);
        when(apiBuilder.build()).thenReturn(ollamaApi);
        when(modelBuilder.ollamaApi(ollamaApi)).thenReturn(modelBuilder);
        when(modelBuilder.options(any(OllamaChatOptions.class))).thenReturn(modelBuilder);
        when(modelBuilder.build()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(output);
        when(output.getText()).thenReturn("Local recap");

        try (MockedStatic<OllamaApi> apiStatic = mockStatic(OllamaApi.class);
             MockedStatic<OllamaChatModel> modelStatic = mockStatic(OllamaChatModel.class)) {
            apiStatic.when(OllamaApi::builder).thenReturn(apiBuilder);
            modelStatic.when(OllamaChatModel::builder).thenReturn(modelBuilder);
            OllamaRoundRecapClient client = new OllamaRoundRecapClient(
                    "http://localhost:11434", "gemma3:4b");

            assertThat(client.generate(data("bg", "Bulgarian"))).isEqualTo("Local recap");

            ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel).call(prompt.capture());
            assertThat(prompt.getValue().getSystemMessage().getText())
                    .contains("directly in Bulgarian");
            verify(apiBuilder).baseUrl("http://localhost:11434");
            verify(modelBuilder).options(any(OllamaChatOptions.class));
        }
    }

    private RoundRecapPromptData data(String locale, String language) {
        return new RoundRecapPromptData(
                "Test League",
                3,
                locale,
                language,
                List.of(new RoundRecapMatchData(
                        "Alpha (Sofia)", "Beta", 2, 1,
                        List.of("Alpha, Alex, minute 12"))),
                List.of(new RoundRecapStandingData(
                        1, "Alpha", 3, 2, 1, 0, 5, 2, 3, 7)));
    }
}
