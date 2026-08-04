package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.web.RecapStoryParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaRecapWriterTest {

    private DataDrivenRoundRecapClient template;
    private RecapValidator validator;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        validator = new RecapValidator();
        template = new DataDrivenRoundRecapClient(
                new RecapStoryCatalog(messageSource), new EditorialDesk(), validator);
    }

    @Test
    void disabled_returnsTheBaseUntouched() {
        OllamaRecapWriter writer = writer(false, (system, user) -> {
            throw new IllegalStateException("must not be called");
        });
        String base = template.generate(roundData());

        assertThat(writer.restyle(base, roundData())).isEqualTo(base);
    }

    @Test
    void enabled_rewritesTheNarrativeAndKeepsTheRest() {
        OllamaRecapWriter writer = writer(true, (system, user) ->
                "1 ~~ Alpha make a statement ~~ Alpha thumped Beta.\n"
                        + "2 ~~ Delta answer on the road ~~ Delta won away at Gama.");
        String base = template.generate(roundData());

        String result = writer.restyle(base, roundData());
        List<RecapStory> stories = RecapStoryParser.parse(result);
        RecapStory lead = stories.stream()
                .filter(story -> story.kind().getFamily() == RecapStoryFamily.NARRATIVE)
                .findFirst().orElseThrow();

        assertThat(lead.headline()).isEqualTo("Alpha make a statement");
        assertThat(result).isNotEqualTo(base);
        assertThat(resultBody(stories, RecapStoryKind.RESULTS))
                .isEqualTo(resultBody(RecapStoryParser.parse(base), RecapStoryKind.RESULTS));
    }

    @Test
    void malformedResponse_keepsTheBase() {
        assertRestyleKeepsBase((system, user) -> "no delimiters at all");
    }

    @Test
    void providerFailure_keepsTheBase() {
        assertRestyleKeepsBase((system, user) -> {
            throw new RuntimeException("ollama down");
        });
    }

    @Test
    void blankResponse_keepsTheBase() {
        assertRestyleKeepsBase((system, user) -> "   ");
    }

    @Test
    void nullResponse_keepsTheBase() {
        assertRestyleKeepsBase((system, user) -> null);
    }

    @Test
    void unsupportedRewrite_isRejectedAndTheBaseKept() {
        assertRestyleKeepsBase((system, user) -> "1 ~~ Ghosts rise ~~ Ghost Town swept everything aside.");
    }

    @Test
    void outOfRangeAndEmptyLines_areIgnored() {
        assertRestyleKeepsBase((system, user) ->
                "9 ~~ Out of range ~~ Alpha did well.\n"
                        + "0 ~~ Below range ~~ Alpha also did well.\n"
                        + "2 ~~    ~~ Alpha again.\n"
                        + "garbage line");
    }

    @Test
    void baseWithoutNarrative_keepsTheBase() {
        OllamaRecapWriter writer = writer(true, (system, user) -> {
            throw new IllegalStateException("must not be called");
        });
        String base = template.generate(new RoundRecapPromptData("Test League", 3, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 0, 0, List.of())),
                standings(), 6, List.of(), List.of(), null));

        assertThat(writer.restyle(base, roundData())).isEqualTo(base);
    }

    @Test
    void systemPrompt_switchesLanguageByLocale() {
        OllamaRecapWriter writer = writer(true, (system, user) -> null);

        assertThat(writer.systemPrompt(localeData("bg"))).contains("български");
        assertThat(writer.systemPrompt(localeData("de"))).contains("Deutsch");
        assertThat(writer.systemPrompt(localeData("en"))).contains("English");
        assertThat(writer.systemPrompt(localeData(null))).contains("English");
    }

    @Test
    void userPrompt_namesTheLeaderWhenStandingsExist() {
        OllamaRecapWriter writer = writer(true, (system, user) -> null);
        RecapStory story = new RecapStory(RecapStoryKind.MVP, 40, "Head", "Body");

        assertThat(writer.userPrompt(List.of(story), roundData()))
                .contains("Round: 3").contains("Current leader: Alpha");

        RoundRecapPromptData withUnplayedLeader = new RoundRecapPromptData("L", 3, "en", "English",
                List.of(), List.of(new RoundRecapStandingData(1, "Unplayed", 0, 0, 0, 0, 0, 0, 0, 0, false),
                new RoundRecapStandingData(2, "Alpha", 3, 2, 1, 0, 9, 2, 7, 7, false)),
                6, List.of(), List.of(), null);
        assertThat(writer.userPrompt(List.of(story), withUnplayedLeader)).contains("Current leader: Alpha");

        RoundRecapPromptData season = new RoundRecapPromptData("L", 0, "en", "English",
                List.of(), null, 6, List.of(), List.of(), null);
        assertThat(writer.userPrompt(List.of(story), season))
                .contains("Season to date").doesNotContain("Current leader");
    }

    private void assertRestyleKeepsBase(RecapChatModel chatModel) {
        OllamaRecapWriter writer = writer(true, chatModel);
        String base = template.generate(roundData());

        assertThat(writer.restyle(base, roundData())).isEqualTo(base);
    }

    private OllamaRecapWriter writer(boolean enabled, RecapChatModel chatModel) {
        return new OllamaRecapWriter(validator, chatModel, enabled);
    }

    private RoundRecapPromptData roundData() {
        return new RoundRecapPromptData("Test League", 3, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 5, 0, List.of()),
                        new RoundRecapMatchData("Gama", "Delta", 1, 4, List.of())),
                standings(), 6, List.of(), List.of(), null);
    }

    private RoundRecapPromptData localeData(String localeTag) {
        return new RoundRecapPromptData("Test League", 3, localeTag, "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 0, List.of())),
                standings(), 6, List.of(), List.of(), null);
    }

    private List<RoundRecapStandingData> standings() {
        return List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 2, 7, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 1, 1, 4, 6, -2, 4, false),
                new RoundRecapStandingData(3, "Gama", 3, 1, 0, 2, 5, 7, -2, 3, false),
                new RoundRecapStandingData(4, "Delta", 3, 0, 1, 2, 3, 6, -3, 1, false));
    }

    private String resultBody(List<RecapStory> stories, RecapStoryKind kind) {
        return stories.stream().filter(story -> story.kind() == kind)
                .findFirst().orElseThrow().body();
    }
}
