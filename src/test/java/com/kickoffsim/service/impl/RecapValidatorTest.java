package com.kickoffsim.service.impl;

import com.kickoffsim.dto.GoalFact;
import com.kickoffsim.dto.MatchFact;
import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPlayerData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.model.Half;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecapValidatorTest {

    private final RecapValidator validator = new RecapValidator();

    @Test
    void validate_dropsUnresolvedKeyInBody() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.UPSET, 50, "Alpha stun Beta", "recap.story.upset.body")), data()))
                .isEmpty();
    }

    @Test
    void validate_dropsUnresolvedKeyInHeadline() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.UPSET, 50, "recap.story.upset.head.1", "Alpha beat Beta")), data()))
                .isEmpty();
    }

    @Test
    void validate_dropsNarrativeAboutUnknownNames() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.UPSET, 50, "Zeta stun Yota", "Zeta beat Yota")), data()))
                .isEmpty();
    }

    @Test
    void validate_keepsNarrativeAboutKnownTeams() {
        RecapStory story = new RecapStory(RecapStoryKind.UPSET, 50, "Alpha stun Beta", "Alpha beat Beta 1:0");

        assertThat(validator.validate(List.of(story), data())).containsExactly(story);
    }

    @Test
    void validate_keepsStatsAndListsRegardlessOfNames() {
        RecapStory stats = new RecapStory(RecapStoryKind.STATS, 1, "", "3::matches");
        RecapStory squad = new RecapStory(RecapStoryKind.SQUAD, 10, "Team", "Zeta::Yota::1::0");

        assertThat(validator.validate(List.of(stats, squad), data())).containsExactly(stats, squad);
    }

    @Test
    void validate_dropsBlankHeadlineNarrative() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.MVP, 40, "   ", "Alpha did well")), data()))
                .isEmpty();
    }

    @Test
    void validate_dropsStoryWithNullHeadline() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.MVP, 40, null, "Alpha")), data()))
                .isEmpty();
    }

    @Test
    void validate_dropsStoryWithNullBody() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.MVP, 40, "Alpha shine", null)), data()))
                .isEmpty();
    }

    @Test
    void validate_acceptsNamesDrawnFromTypedFactsAndCharts() {
        List<RecapStory> stories = List.of(
                new RecapStory(RecapStoryKind.BREAKOUT, 40, "Gunner breaks through", "Gunner scored"),
                new RecapStory(RecapStoryKind.MVP, 40, "Helper sets up", "Helper assisted"),
                new RecapStory(RecapStoryKind.SCORER_RACE, 40, "Scorer leads", "Scorer tops the chart"));

        assertThat(validator.validate(stories, factsData())).hasSize(3);
    }

    @Test
    void validate_dropsNamesAbsentFromTypedFacts() {
        assertThat(validator.validate(List.of(
                new RecapStory(RecapStoryKind.UPSET, 50, "Ghost stun Nobody", "Ghost beat Nobody")), factsData()))
                .isEmpty();
    }

    private RoundRecapPromptData data() {
        return new RoundRecapPromptData("League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 0, List.of())),
                List.of(new RoundRecapStandingData(1, "Alpha", 1, 1, 0, 0, 1, 0, 1, 3, false)),
                3, List.of(), List.of(), null);
    }

    private RoundRecapPromptData factsData() {
        MatchFact match = new MatchFact("m", 3, "Alpha", "Beta", 2, 0, List.of(
                new GoalFact(true, "Alpha", "Gunner", "Helper", 10, Half.FIRST, false, false),
                new GoalFact(true, "Alpha", null, null, 20, Half.SECOND, false, false),
                new GoalFact(true, "Alpha", "OwnScorer", null, 30, Half.SECOND, false, true)));
        return new RoundRecapPromptData("League", 3, "en", "English",
                null, null, 3,
                List.of(new RoundRecapPlayerData("Scorer", "Alpha", 9)), null, null,
                List.of(match), null, null);
    }
}
