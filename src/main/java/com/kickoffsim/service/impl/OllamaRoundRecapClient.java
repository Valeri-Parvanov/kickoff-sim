package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.service.RoundRecapAiClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaRoundRecapClient implements RoundRecapAiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaRoundRecapClient.class);

    private final ChatOperation chatOperation;

    @Autowired
    public OllamaRoundRecapClient(
            @Value("${kickoffsim.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${kickoffsim.ollama.model:gemma3:4b}") String ollamaModel) {
        this.chatOperation = (system, user) -> request(system, user, ollamaBaseUrl, ollamaModel);
    }

    OllamaRoundRecapClient(ChatOperation chatOperation) {
        this.chatOperation = chatOperation;
    }

    @Override
    public String generate(RoundRecapPromptData data) {
        try {
            String content = chatOperation.call(systemPrompt(data), userPrompt(data));
            if (content == null || content.isBlank()) {
                throw new RoundRecapGenerationException("The AI provider returned an empty round recap.");
            }
            return content.trim();
        } catch (RoundRecapGenerationException ex) {
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("Round recap generation failed through the configured AI provider.", ex);
            throw new RoundRecapGenerationException("The AI provider could not generate the round recap.", ex);
        }
    }

    private String request(String system, String user, String baseUrl, String model) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(0.1)
                        .numCtx(8192)
                        .numPredict(420)
                        .keepAlive("30m")
                        .build())
                .build();
        return chatModel.call(new Prompt(
                        new SystemMessage(system),
                        new UserMessage(user)))
                .getResult()
                .getOutput()
                .getText();
    }

    String systemPrompt(RoundRecapPromptData data) {
        String languageInstruction = switch (data.localeTag()) {
            case "bg" -> "Пиши целия отговор единствено на естествен български език. Не използвай английски или немски думи и изречения.";
            case "de" -> "Schreibe die gesamte Antwort ausschließlich in natürlichem Deutsch. Verwende keine englischen oder bulgarischen Sätze.";
            default -> "Write the entire response exclusively in natural English. Do not use Bulgarian or German sentences.";
        };
        String scopeInstruction = data.roundNumber() == 0
                ? "Write a season overview based on all supplied completed matches and the current standings."
                : "Write an overview of the supplied completed round.";
        return """
                %s
                You are an experienced football journalist. %s Write the original analysis directly in %s.
                Do not translate text from another language and do not mention translation.
                Use only the supplied facts. Every claim must be verifiable from the match results, goal events,
                teams, and standings below. Never invent statistics, context, quotes, injuries, tactics, records,
                attendance, form, penalty shootouts, or events. If a fact is not supplied, omit it.
                Treat the VERIFIED KEY FACTS section as authoritative. Copy every team name and score exactly.
                Do not calculate new statistics and do not reinterpret points, goals, margins, or table positions.
                Write 3 to 4 compact but informative paragraphs of natural sports journalism.
                The recap must identify the current league leader and points total from position 1 in the standings.
                It must highlight the most important verifiable results: the largest winning margin, a close or
                high-scoring match, and relevant listed goal events when supplied. Compare the results before choosing them.
                Call a match a derby only when the supplied facts prove that description; otherwise call it a key match.
                Do not list every match mechanically and do not use bullet points.
                Return only the recap, without a title, markdown, bullet points, or preamble.
                End by checking that every sentence is in %s and every claim exists in the supplied data.
                """.formatted(languageInstruction, scopeInstruction, data.languageName(), data.languageName());
    }

    String userPrompt(RoundRecapPromptData data) {
        RoundRecapStandingData leader = data.standings().stream()
                .min(java.util.Comparator.comparingInt(RoundRecapStandingData::position))
                .orElseThrow();
        RoundRecapMatchData largestWin = data.matches().stream()
                .max(java.util.Comparator.comparingInt(this::goalMargin))
                .orElseThrow();
        RoundRecapMatchData highestScoring = data.matches().stream()
                .max(java.util.Comparator.comparingInt(this::totalGoals))
                .orElseThrow();
        RoundRecapMatchData closest = data.matches().stream()
                .min(java.util.Comparator.comparingInt(this::goalMargin))
                .orElseThrow();
        StringBuilder prompt = new StringBuilder()
                .append("League: ").append(data.leagueName()).append('\n')
                .append(data.roundNumber() == 0 ? "Scope: Season to date\n" : "Round: " + data.roundNumber() + '\n')
                .append("Output locale: ").append(data.localeTag()).append("\n\n")
                .append("Mandatory: write only in ").append(data.languageName())
                .append(". Position 1 below is the current leader and must be named with its points total.\n\n")
                .append("VERIFIED KEY FACTS:\n")
                .append("- Current leader: ").append(leader.team())
                .append(" with ").append(leader.points()).append(" points.\n")
                .append("- Largest winning margin: ").append(matchFact(largestWin)).append(".\n")
                .append("- Highest-scoring match: ").append(matchFact(highestScoring)).append(".\n")
                .append("- Closest result: ").append(matchFact(closest)).append(".\n")
                .append("Use these exact facts. Do not add explanations that are absent from the data.\n\n")
                .append("Completed matches:\n");
        data.matches().forEach(match -> {
            prompt.append("- ").append(match.homeTeam()).append(' ')
                    .append(match.homeScore()).append(':').append(match.awayScore()).append(' ')
                    .append(match.awayTeam()).append('\n');
            match.goals().forEach(goal -> prompt.append("  Goal: ").append(goal).append('\n'));
        });
        prompt.append("\nCurrent verified standings:\n");
        data.standings().forEach(row -> prompt.append(row.position()).append(". ")
                .append(row.team()).append(" | P ").append(row.played())
                .append(" W ").append(row.wins())
                .append(" D ").append(row.draws())
                .append(" L ").append(row.losses())
                .append(" GF ").append(row.goalsFor())
                .append(" GA ").append(row.goalsAgainst())
                .append(" GD ").append(row.goalDifference())
                .append(" PTS ").append(row.points()).append('\n'));
        prompt.append("\nReturn only 3 to 4 prose paragraphs in ")
                .append(data.languageName())
                .append(". Do not use bullet points and do not add facts absent above.");
        return prompt.toString();
    }

    private int goalMargin(RoundRecapMatchData match) {
        return Math.abs(match.homeScore() - match.awayScore());
    }

    private int totalGoals(RoundRecapMatchData match) {
        return match.homeScore() + match.awayScore();
    }

    private String matchFact(RoundRecapMatchData match) {
        return match.homeTeam() + " " + match.homeScore() + ":"
                + match.awayScore() + " " + match.awayTeam();
    }

    @FunctionalInterface
    interface ChatOperation {
        String call(String system, String user);
    }
}
