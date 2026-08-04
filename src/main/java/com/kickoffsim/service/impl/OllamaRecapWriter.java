package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.web.RecapStoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OllamaRecapWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaRecapWriter.class);

    private static final int SEASON_SCOPE = 0;

    private static final String FIELD = "~~";

    private static final Pattern LINE = Pattern.compile("^\\D*(\\d+)\\s*~~\\s*(.+?)\\s*~~\\s*(.+)$");

    private final RecapValidator validator;
    private final RecapChatModel chatModel;
    private final boolean enabled;

    public OllamaRecapWriter(RecapValidator validator,
                             RecapChatModel chatModel,
                             @Value("${kickoffsim.ollama.enabled:true}") boolean enabled) {
        this.validator = validator;
        this.chatModel = chatModel;
        this.enabled = enabled;
    }

    public String restyle(String base, RoundRecapPromptData data) {
        if (!enabled) {
            return base;
        }
        List<RecapStory> stories = RecapStoryParser.parse(base);
        List<RecapStory> narrative = stories.stream()
                .filter(story -> story.kind().getFamily() == RecapStoryFamily.NARRATIVE)
                .toList();
        if (narrative.isEmpty()) {
            return base;
        }
        try {
            Map<Integer, RecapStory> rewrites = rewrite(narrative, data);
            if (rewrites.isEmpty()) {
                return base;
            }
            return RecapStoryParser.serialize(merge(stories, rewrites));
        } catch (RuntimeException exception) {
            LOGGER.warn("Ollama restyle unavailable, keeping the template recap: {}", exception.getMessage());
            return base;
        }
    }

    private List<RecapStory> merge(List<RecapStory> stories, Map<Integer, RecapStory> rewrites) {
        List<RecapStory> merged = new ArrayList<>();
        int index = 0;
        for (RecapStory story : stories) {
            if (story.kind().getFamily() == RecapStoryFamily.NARRATIVE) {
                index++;
                merged.add(rewrites.getOrDefault(index, story));
            } else {
                merged.add(story);
            }
        }
        return merged;
    }

    private Map<Integer, RecapStory> rewrite(List<RecapStory> narrative, RoundRecapPromptData data) {
        String response = chatModel.complete(systemPrompt(data), userPrompt(narrative, data));
        if (response == null || response.isBlank()) {
            throw new RoundRecapGenerationException("The AI provider returned an empty recap.");
        }
        Map<Integer, RecapStory> rewrites = new HashMap<>();
        for (String line : response.split("\n")) {
            Matcher matcher = LINE.matcher(line.strip());
            if (!matcher.matches()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > narrative.size()) {
                continue;
            }
            String headline = sanitize(matcher.group(2));
            String body = sanitize(matcher.group(3));
            if (headline.isEmpty()) {
                continue;
            }
            RecapStory original = narrative.get(index - 1);
            RecapStory candidate = new RecapStory(original.kind(), original.weight(), headline, body);
            if (validator.validate(List.of(candidate), data).isEmpty()) {
                continue;
            }
            rewrites.put(index, candidate);
        }
        return rewrites;
    }

    private String sanitize(String text) {
        return text.replace('|', '/').replace('\r', ' ').replace('\n', ' ').strip();
    }

    String systemPrompt(RoundRecapPromptData data) {
        String language = switch (data.localeTag() == null ? "en" : data.localeTag()) {
            case "bg" -> "Пиши единствено на естествен български език.";
            case "de" -> "Schreibe ausschließlich in natürlichem Deutsch.";
            default -> "Write exclusively in natural English.";
        };
        return """
                You are an experienced football journalist. %s
                You will receive numbered facts, one per line. Rewrite each into a confident, natural headline
                and a short one or two sentence analysis, as a professional would write it.
                Use only the information in that line. Never invent teams, players, scores, statistics, motives,
                form, tactics, quotes, or history. Keep every name and number exactly as given.
                Vary the wording and rhythm; avoid clichés and hollow hype.
                Output exactly one line per fact, in the format:
                <number> %s <headline> %s <analysis>
                Return only those lines, no preamble, no markdown, no bullet points.
                """.formatted(language, FIELD, FIELD);
    }

    String userPrompt(List<RecapStory> narrative, RoundRecapPromptData data) {
        StringBuilder prompt = new StringBuilder()
                .append("League: ").append(data.leagueName()).append('\n')
                .append(data.roundNumber() == SEASON_SCOPE
                        ? "Scope: Season to date\n" : "Round: " + data.roundNumber() + '\n')
                .append("Language tag: ").append(data.localeTag()).append('\n');
        leader(data).ifPresent(leader -> prompt.append("Current leader: ").append(leader.team())
                .append(" on ").append(leader.points()).append(" points.\n"));
        prompt.append("\nRewrite these ").append(narrative.size()).append(" facts:\n");
        for (int i = 0; i < narrative.size(); i++) {
            RecapStory story = narrative.get(i);
            prompt.append(i + 1).append(' ').append(FIELD).append(' ')
                    .append(story.headline()).append(' ').append(FIELD).append(' ')
                    .append(story.body()).append('\n');
        }
        return prompt.toString();
    }

    private Optional<RoundRecapStandingData> leader(RoundRecapPromptData data) {
        return data.standings() == null ? Optional.empty()
                : data.standings().stream().filter(row -> row.played() > 0)
                        .min(Comparator.comparingInt(RoundRecapStandingData::position));
    }
}
