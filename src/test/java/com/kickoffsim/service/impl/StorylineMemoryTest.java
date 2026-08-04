package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapMemory;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.RoundRecapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorylineMemoryTest {

    @Mock
    private RoundRecapRepository repository;

    private StorylineMemory memory;
    private UUID leagueId;

    @BeforeEach
    void setUp() {
        memory = new StorylineMemory(repository);
        leagueId = UUID.randomUUID();
    }

    @Test
    void recall_noHistory_isEmpty() {
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 2)).thenReturn(List.of());

        RecapMemory result = memory.recall(leagueId, 3, "en", false);

        assertThat(result.recentAngles()).isEmpty();
        assertThat(result.recentHeadlines()).isEmpty();
        assertThat(result.regeneration()).isFalse();
    }

    @Test
    void recall_firstRound_readsNoHistory() {
        RecapMemory result = memory.recall(leagueId, 1, "en", false);

        assertThat(result.recentAngles()).isEmpty();
    }

    @Test
    void recall_collectsNarrativeAnglesNewestFirstAndSkipsStats() {
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 2)).thenReturn(List.of(
                recap("SURGE|50|Alpha are surging|b\nSTATS|1||3::matches"),
                recap("COMEBACK|60|Beta turn it around|b")));

        RecapMemory result = memory.recall(leagueId, 3, "en", false);

        assertThat(result.recentAngles()).containsExactly("SURGE", "COMEBACK");
        assertThat(result.recentHeadlines()).containsExactly("Alpha are surging", "Beta turn it around");
    }

    @Test
    void recall_regeneration_putsThePreviousVersionFirst() {
        when(repository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 3, "en"))
                .thenReturn(Optional.of(recap("TITLE_BATTLE|80|Alpha and Beta go toe to toe|b")));
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 2)).thenReturn(List.of(recap("SURGE|50|Alpha are surging|b")));

        RecapMemory result = memory.recall(leagueId, 3, "en", true);

        assertThat(result.recentAngles()).containsExactly("TITLE_BATTLE", "SURGE");
        assertThat(result.regeneration()).isTrue();
    }

    @Test
    void recall_looksBackAtMostThreeRounds() {
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 4)).thenReturn(List.of(
                recap("UPSET|50|r4|b"),
                recap("SURGE|50|r3|b"),
                recap("COLLAPSE|50|r2|b"),
                recap("COMEBACK|50|r1|b")));

        RecapMemory result = memory.recall(leagueId, 5, "en", false);

        assertThat(result.recentAngles()).containsExactly("UPSET", "SURGE", "COLLAPSE");
    }

    @Test
    void recall_season_looksAcrossEveryRound() {
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, Integer.MAX_VALUE)).thenReturn(List.of(recap("STREAK|75|Alpha roll on|b")));

        RecapMemory result = memory.recall(leagueId, 0, "en", false);

        assertThat(result.recentAngles()).containsExactly("STREAK");
    }

    @Test
    void recall_regenerationWithoutAStoredVersion_usesOnlyEarlierRounds() {
        when(repository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 3, "en"))
                .thenReturn(Optional.empty());
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 2)).thenReturn(List.of(recap("SURGE|50|Alpha surge|b")));

        RecapMemory result = memory.recall(leagueId, 3, "en", true);

        assertThat(result.recentAngles()).containsExactly("SURGE");
    }

    @Test
    void recall_deduplicatesRepeatedHeadlines() {
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 2)).thenReturn(List.of(
                recap("UPSET|50|Same headline|b"),
                recap("SURGE|50|Same headline|b")));

        RecapMemory result = memory.recall(leagueId, 3, "en", false);

        assertThat(result.recentHeadlines()).containsExactly("Same headline");
        assertThat(result.recentAngles()).containsExactly("UPSET", "SURGE");
    }

    @Test
    void recall_capsAnglesAndHeadlines() {
        String[] kinds = {"UPSET", "SURGE", "COLLAPSE", "BREAKOUT", "COMEBACK", "LATE_DRAMA",
                "SWINGS", "HAT_TRICK", "MVP", "STREAK", "BIG_WIN", "AWAY_WIN", "GOAL_FEST"};
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < kinds.length; i++) {
            if (i > 0) {
                content.append("\n");
            }
            content.append(kinds[i]).append("|50|Headline ").append(i).append("|body");
        }
        when(repository.findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                leagueId, "en", 1, 4)).thenReturn(List.of(recap(content.toString())));

        RecapMemory result = memory.recall(leagueId, 5, "en", false);

        assertThat(result.recentAngles()).hasSize(6);
        assertThat(result.recentHeadlines()).hasSize(12);
    }

    private RoundRecap recap(String content) {
        RoundRecap recap = new RoundRecap();
        recap.setContent(content);
        return recap;
    }
}
