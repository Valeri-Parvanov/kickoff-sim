package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPlayerData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.web.RecapStoryParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataDrivenRoundRecapClientTest {

    private static final int SEASON = 0;

    private DataDrivenRoundRecapClient client;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        client = new DataDrivenRoundRecapClient(new RecapStoryCatalog(messageSource));
    }

    @Test
    void generate_producesContentThatParsesBackIntoStories() {
        List<RecapStory> stories = stories(roundData("en", 3));

        assertThat(stories).isNotEmpty();
        assertThat(stories).allSatisfy(story -> {
            assertThat(story.headline()).doesNotContain("recap.");
            assertThat(story.body()).doesNotContain("recap.");
        });
    }

    @Test
    void generate_ordersTheNarrativeStoriesByWeight() {
        List<RecapStory> narrative = byFamily(stories(roundData("en", 3)), RecapStoryFamily.NARRATIVE);

        assertThat(narrative).isSortedAccordingTo(Comparator.comparingInt(RecapStory::weight).reversed());
    }

    @Test
    void generate_keepsAtMostSixNarrativeStories() {
        List<RecapStory> narrative = byFamily(stories(crowdedRound()), RecapStoryFamily.NARRATIVE);

        assertThat(narrative).hasSize(6);
    }

    @Test
    void generate_putsTheStatsStripAndThePanelsAfterTheStories() {
        List<RecapStory> stories = stories(roundData("en", 3));

        int lastNarrative = lastIndexOf(stories, RecapStoryFamily.NARRATIVE);
        int stats = lastIndexOf(stories, RecapStoryFamily.STATS);
        int firstList = stories.indexOf(byFamily(stories, RecapStoryFamily.LIST).get(0));

        assertThat(stats).isGreaterThan(lastNarrative);
        assertThat(firstList).isGreaterThan(stats);
    }

    @Test
    void generate_ordersThePanelsSquadBenchResults() {
        List<RecapStory> lists = byFamily(stories(squadRound()), RecapStoryFamily.LIST);

        assertThat(lists).extracting(RecapStory::kind).containsExactly(
                RecapStoryKind.SQUAD, RecapStoryKind.BENCH, RecapStoryKind.RESULTS);
    }

    @Test
    void generate_storiesOfEqualWeight_areOrderedByKind() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", SEASON, "en", "English",
                matches(), standings(), 3,
                List.of(new RoundRecapPlayerData("Petar", "Alpha", 12),
                        new RoundRecapPlayerData("Ivan", "Beta", 2)),
                List.of());

        List<RecapStory> narrative = byFamily(stories(data), RecapStoryFamily.NARRATIVE);
        int scorerRace = indexOfKind(narrative, RecapStoryKind.SCORER_RACE);
        int secondPlace = indexOfKind(narrative, RecapStoryKind.SECOND_PLACE);

        assertThat(narrative.get(scorerRace).weight()).isEqualTo(narrative.get(secondPlace).weight());
        assertThat(scorerRace).isEqualTo(secondPlace - 1);
    }

    @Test
    void generate_roundRecap_hasNoSeasonOnlyStories() {
        List<RecapStory> stories = stories(roundData("en", 3));

        assertThat(stories).extracting(RecapStory::kind)
                .doesNotContain(RecapStoryKind.TITLE_RACE, RecapStoryKind.SECOND_PLACE,
                        RecapStoryKind.BOTTOM, RecapStoryKind.ATTACK_DEFENCE);
    }

    @Test
    void generate_seasonRecap_hasNoRoundOnlyStories() {
        List<RecapStory> stories = stories(roundData("en", SEASON));

        assertThat(stories).extracting(RecapStory::kind)
                .contains(RecapStoryKind.TITLE_RACE)
                .doesNotContain(RecapStoryKind.SQUAD, RecapStoryKind.RESULTS, RecapStoryKind.MVP);
    }

    @Test
    void generate_nullData_throws() {
        assertThatThrownBy(() -> client.generate(null))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("No data");
    }

    @Test
    void generate_noMatches_throws() {
        assertThatThrownBy(() -> client.generate(data(1, List.of())))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("no played matches");
    }

    @Test
    void generate_nullMatches_throws() {
        assertThatThrownBy(() -> client.generate(data(1, null)))
                .isInstanceOf(RoundRecapGenerationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "bg", "de"})
    void generate_everyLocale_resolvesEveryKey(String locale) {
        assertThat(client.generate(roundData(locale, SEASON))).doesNotContain("recap.");
        assertThat(client.generate(roundData(locale, 3))).doesNotContain("recap.");
    }

    @Test
    void generate_nullLocaleTag_fallsBackToEnglish() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, null, null,
                matches(), standings(), 3, List.of(), List.of());

        assertThat(client.generate(data)).doesNotContain("recap.");
    }

    @Test
    void generate_isDeterministic() {
        assertThat(client.generate(roundData("bg", 4))).isEqualTo(client.generate(roundData("bg", 4)));
    }

    private List<RecapStory> stories(RoundRecapPromptData data) {
        return RecapStoryParser.parse(client.generate(data));
    }

    private List<RecapStory> byFamily(List<RecapStory> stories, RecapStoryFamily family) {
        return stories.stream().filter(story -> story.kind().getFamily() == family).toList();
    }

    private int indexOfKind(List<RecapStory> stories, RecapStoryKind kind) {
        return stories.indexOf(stories.stream()
                .filter(story -> story.kind() == kind)
                .findFirst()
                .orElseThrow());
    }

    private int lastIndexOf(List<RecapStory> stories, RecapStoryFamily family) {
        List<RecapStory> matching = byFamily(stories, family);
        return stories.indexOf(matching.get(matching.size() - 1));
    }

    private RoundRecapPromptData roundData(String locale, int round) {
        return new RoundRecapPromptData("Test League", round, locale, "English",
                matches(), standings(), 6, List.of(), List.of());
    }

    private RoundRecapPromptData data(int round, List<RoundRecapMatchData> matches) {
        return new RoundRecapPromptData("Test League", round, "en", "English",
                matches, standings(), 6, List.of(), List.of());
    }

    private RoundRecapPromptData crowdedRound() {
        return data(2, List.of(
                new RoundRecapMatchData("Alpha", "Beta", 4, 5, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Beta, Ivan, minute 6, first half",
                        "Beta, Ivan, minute 9, first half",
                        "Alpha, Petar, minute 12, first half",
                        "Alpha, Petar, minute 2, second half",
                        "Beta, Dimitar, minute 5, second half",
                        "Beta, Dimitar, minute 8, second half",
                        "Alpha, Georgi, minute 11, second half",
                        "Beta, Stefan, minute 18, second half")),
                new RoundRecapMatchData("Gama", "Delta", 3, 2, List.of(
                        "Delta, Nikola, minute 4, first half",
                        "Delta, Nikola, minute 9, first half",
                        "Gama, Todor, minute 1, second half",
                        "Gama, Todor, minute 6, second half",
                        "Gama, Mitko, minute 12, second half")),
                new RoundRecapMatchData("Epsilon", "Zeta", 0, 6, List.of())));
    }

    private RoundRecapPromptData squadRound() {
        return data(2, List.of(
                new RoundRecapMatchData("Alpha", "Beta", 4, 0, List.of(
                        "Alpha, Asen, minute 3, first half",
                        "Alpha, Boris, minute 6, first half",
                        "Alpha, Chavdar, minute 9, first half",
                        "Alpha, Dimitar, minute 12, first half")),
                new RoundRecapMatchData("Gama", "Delta", 4, 0, List.of(
                        "Gama, Emil, minute 3, first half",
                        "Gama, Filip, minute 6, first half",
                        "Gama, Georgi, minute 9, first half",
                        "Gama, Hristo, minute 12, first half"))));
    }

    private List<RoundRecapMatchData> matches() {
        return List.of(
                new RoundRecapMatchData("Alpha", "Beta", 5, 0, List.of()),
                new RoundRecapMatchData("Gama", "Delta", 3, 3, List.of()),
                new RoundRecapMatchData("Beta", "Gama", 0, 0, List.of()));
    }

    private List<RoundRecapStandingData> standings() {
        return List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 2, 7, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 1, 1, 4, 6, -2, 4, false),
                new RoundRecapStandingData(3, "Gama", 3, 1, 0, 2, 5, 7, -2, 3, false),
                new RoundRecapStandingData(4, "Delta", 3, 0, 1, 2, 3, 6, -3, 1, false));
    }
}
