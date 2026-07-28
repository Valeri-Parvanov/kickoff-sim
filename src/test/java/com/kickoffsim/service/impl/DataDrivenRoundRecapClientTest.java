package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataDrivenRoundRecapClientTest {

    private DataDrivenRoundRecapClient client;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        client = new DataDrivenRoundRecapClient(messageSource);
    }

    @Test
    void generate_roundRecap_reportsVolumeAndResults() {
        String recap = client.generate(roundData("en", 3));

        assertThat(recap).contains("Test League");
        assertThat(recap).contains("Round 3");
        assertThat(recap).contains("3 matches played");
        assertThat(recap).contains("11 goals");
        assertThat(recap).contains("Alpha 5:0 Beta");
        assertThat(recap).contains("Gama 3:3 Delta");
    }

    @Test
    void generate_identifiesBiggestWinAndRichestMatch() {
        String recap = client.generate(roundData("en", 3));

        assertThat(recap).contains("Alpha beat Beta 5:0, a margin of 5");
        assertThat(recap).contains("Gama vs Delta with 6 goals");
        assertThat(recap).contains("Draws: 2");
    }

    @Test
    void generate_awayWin_namesTheWinnerNotTheHomeTeam() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 0, 4, List.of())),
                standings());

        assertThat(client.generate(data)).contains("Beta beat Alpha 4:0");
    }

    @Test
    void generate_roundRecap_omitsTheStandings() {
        String recap = client.generate(roundData("en", 3));

        assertThat(recap).doesNotContain("Leader:");
        assertThat(recap).doesNotContain("Runner-up:");
        assertThat(recap).doesNotContain("Best attack");
        assertThat(recap).doesNotContain("Best defence");
        assertThat(recap).doesNotContain("Champion");
    }

    @Test
    void generate_roundRecap_reportsComebackWin() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 3, 2, List.of(
                        "Beta, Ivan, minute 5, first half",
                        "Beta, Ivan, minute 12, first half",
                        "Alpha, Petar, minute 3, second half",
                        "Alpha, Petar, minute 9, second half",
                        "Alpha, Georgi, minute 14, second half"))),
                standings());

        assertThat(client.generate(data)).contains("Comeback: Alpha trailed Beta by 2 goals and won 3:2");
    }

    @Test
    void generate_roundRecap_reportsComebackDraw() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 2, List.of(
                        "Beta, Ivan, minute 5, first half",
                        "Beta, Ivan, minute 12, first half",
                        "Alpha, Petar, minute 3, second half",
                        "Alpha, Georgi, minute 9, second half"))),
                standings());

        assertThat(client.generate(data)).contains("Rescue act: Alpha trailed Beta by 2 goals and salvaged 2:2");
    }

    @Test
    void generate_roundRecap_reportsLateWinner() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of(
                        "Alpha, Petar, minute 10, first half",
                        "Beta, Ivan, minute 4, second half",
                        "Alpha, Georgi, minute 18, second half"))),
                standings());

        assertThat(client.generate(data))
                .contains("Late drama: Alpha settled it against Beta in the 18th minute of the second half for 2:1");
    }

    @Test
    void generate_roundRecap_reportsLateEqualiser() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 1, List.of(
                        "Alpha, Petar, minute 10, first half",
                        "Beta, Ivan, minute 20, second half"))),
                standings());

        assertThat(client.generate(data))
                .contains("Late drama: Beta equalised against Alpha in the 20th minute of the second half for 1:1");
    }

    @Test
    void generate_roundRecap_putsAllResultsAfterTheHighlights() {
        String recap = client.generate(roundData("en", 3));

        assertThat(recap.indexOf("Results:")).isGreaterThan(recap.indexOf("Most goals in a match"));
    }

    @Test
    void generate_roundRecap_reportsEveryLeadChangeInTheWildestMatch() {
        String recap = client.generate(swingData());

        assertThat(recap).contains("Wildest match: Alpha 4:5 Beta — the lead changed 3 times, "
                + "score by score: 1:0, 1:1, 1:2, 2:2, 3:2, 3:3, 3:4, 4:4, 4:5.");
    }

    @Test
    void generate_matchWithoutEnoughLeadChanges_isNotReported() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Beta, Ivan, minute 6, first half",
                        "Alpha, Georgi, minute 9, first half"))),
                standings());

        assertThat(client.generate(data)).doesNotContain("Wildest match");
    }

    @Test
    void generate_roundRecap_namesThePlayerOfTheRound() {
        String recap = client.generate(swingData());

        assertThat(recap).contains("Player of the round: Petar (Alpha) — 3G 0A.");
    }

    @Test
    void generate_ownGoals_doNotCountTowardsThePlayerOfTheRound() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 0, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Alpha, Ivan, minute 9, first half, own goal"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).contains("Player of the round: Petar (Alpha)");
        assertThat(recap).doesNotContain("Ivan");
    }

    @Test
    void generate_roundRecap_picksTheTopSixPlusBench() {
        String recap = client.generate(squadData());

        assertThat(recap).contains("Team of the round:");
        assertThat(recap).contains("- Asen (Alpha) — 1G 0A");
        assertThat(recap).contains("Bench:");
        assertThat(recap.indexOf("Filip")).isLessThan(recap.indexOf("Bench:"));
        assertThat(recap.indexOf("Bench:")).isLessThan(recap.indexOf("Georgi"));
        assertThat(recap.indexOf("Georgi")).isLessThan(recap.indexOf("Hristo"));
    }

    @Test
    void generate_assists_countTowardsTheTeamOfTheRound() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 0, List.of(
                        "Alpha, Petar, minute 3, first half, assist Georgi"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).contains("- Petar (Alpha) — 1G 0A");
        assertThat(recap).contains("- Georgi (Alpha) — 0G 1A");
    }

    @Test
    void generate_assistFollowedByAnotherField_readsOnlyTheAssistantName() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 0, List.of(
                        "Alpha, Petar, minute 3, first half, assist Georgi, penalty"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).contains("- Georgi (Alpha) — 0G 1A");
        assertThat(recap).doesNotContain("penalty");
    }

    @Test
    void generate_goalWithoutSeparatedFields_isSkipped() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 0, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "unstructured"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).contains("- Petar (Alpha) — 1G 0A");
        assertThat(recap).doesNotContain("unstructured");
    }

    @Test
    void generate_seasonRecap_hasNoTeamOfTheRound() {
        String recap = client.generate(roundData("en", 0));

        assertThat(recap).doesNotContain("Team of the round:");
        assertThat(recap).doesNotContain("Player of the round:");
    }

    private RoundRecapPromptData swingData() {
        return new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 4, 5, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Beta, Ivan, minute 6, first half",
                        "Beta, Ivan, minute 9, first half",
                        "Alpha, Petar, minute 12, first half",
                        "Alpha, Petar, minute 2, second half",
                        "Beta, Dimitar, minute 5, second half",
                        "Beta, Dimitar, minute 8, second half",
                        "Alpha, Georgi, minute 11, second half",
                        "Beta, Stefan, minute 15, second half"))),
                standings());
    }

    private RoundRecapPromptData squadData() {
        return new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(
                        new RoundRecapMatchData("Alpha", "Beta", 4, 0, List.of(
                                "Alpha, Asen, minute 3, first half",
                                "Alpha, Boris, minute 6, first half",
                                "Alpha, Chavdar, minute 9, first half",
                                "Alpha, Dimitar, minute 12, first half")),
                        new RoundRecapMatchData("Gama", "Delta", 4, 0, List.of(
                                "Gama, Emil, minute 3, first half",
                                "Gama, Filip, minute 6, first half",
                                "Gama, Georgi, minute 9, first half",
                                "Gama, Hristo, minute 12, first half"))),
                standings());
    }

    @Test
    void generate_lateGoalThatChangesNothing_isNotReported() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 0, List.of(
                        "Alpha, Petar, minute 10, first half",
                        "Alpha, Georgi, minute 17, second half"))),
                standings());

        assertThat(client.generate(data)).doesNotContain("Late drama");
    }

    @Test
    void generate_timelineThatDoesNotMatchTheScore_isIgnored() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 3, 2, List.of(
                        "Beta, Ivan, minute 5, first half",
                        "Alpha, Petar, minute 18, second half"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Comeback");
        assertThat(recap).doesNotContain("Late drama");
    }

    @Test
    void generate_seasonRecap_omitsComebackAndLateDrama() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 3, 2, List.of(
                        "Beta, Ivan, minute 5, first half",
                        "Beta, Ivan, minute 12, first half",
                        "Alpha, Petar, minute 3, second half",
                        "Alpha, Petar, minute 9, second half",
                        "Alpha, Georgi, minute 18, second half"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Comeback");
        assertThat(recap).doesNotContain("Late drama");
        assertThat(recap).contains("Leader: Alpha");
    }

    @Test
    void generate_awayComeback_namesTheVisitors() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 3, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Alpha, Petar, minute 8, first half",
                        "Beta, Ivan, minute 2, second half",
                        "Beta, Ivan, minute 7, second half",
                        "Beta, Stefan, minute 11, second half"))),
                standings());

        assertThat(client.generate(data)).contains("Comeback: Beta trailed Alpha by 2 goals and won 3:2");
    }

    @Test
    void generate_awayComebackToADraw_namesTheVisitors() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 2, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Alpha, Petar, minute 8, first half",
                        "Beta, Ivan, minute 2, second half",
                        "Beta, Stefan, minute 11, second half"))),
                standings());

        assertThat(client.generate(data))
                .contains("Rescue act: Beta trailed Alpha by 2 goals and salvaged 2:2");
    }

    @Test
    void generate_twoComebacks_prefersTheOneThatEndedInAWin() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(
                        new RoundRecapMatchData("Alpha", "Beta", 2, 2, List.of(
                                "Beta, Ivan, minute 3, first half",
                                "Beta, Ivan, minute 8, first half",
                                "Alpha, Petar, minute 2, second half",
                                "Alpha, Georgi, minute 7, second half")),
                        new RoundRecapMatchData("Gama", "Delta", 3, 2, List.of(
                                "Delta, Nikola, minute 4, first half",
                                "Delta, Nikola, minute 9, first half",
                                "Gama, Todor, minute 1, second half",
                                "Gama, Todor, minute 6, second half",
                                "Gama, Mitko, minute 12, second half"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).contains("Comeback: Gama trailed Delta by 2 goals and won 3:2");
        assertThat(recap).doesNotContain("Rescue act");
    }

    @Test
    void generate_lateGoalWithoutARecordedMinute_isNotReported() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 1, List.of(
                        "Alpha, Petar, minute 10, first half",
                        "Beta, Ivan, minute not recorded, second half"))),
                standings());

        assertThat(client.generate(data)).doesNotContain("Late drama");
    }

    @Test
    void generate_lateGoalInTheFirstHalf_isNotReported() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 1, List.of(
                        "Alpha, Petar, minute 3, first half",
                        "Beta, Ivan, minute 19, first half"))),
                standings());

        assertThat(client.generate(data)).doesNotContain("Late drama");
    }

    @Test
    void generate_matchWithoutGoalTimeline_producesNoDramaLines() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of())),
                standings());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Comeback");
        assertThat(recap).doesNotContain("Late drama");
    }

    @Test
    void generate_seasonWithOnePlayedTeam_omitsTheRunnerUpLine() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                matches(),
                List.of(standings().get(0)));

        String recap = client.generate(data);

        assertThat(recap).contains("Leader: Alpha");
        assertThat(recap).doesNotContain("Runner-up");
        assertThat(recap).doesNotContain("Bottom of the table");
    }

    @Test
    void generate_seasonWithoutPlayedTeams_omitsTheTable() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                matches(),
                List.of(new RoundRecapStandingData(1, "Alpha", 0, 0, 0, 0, 0, 0, 0, 0, false)));

        String recap = client.generate(data);

        assertThat(recap).contains("season review");
        assertThat(recap).doesNotContain("Leader");
        assertThat(recap).doesNotContain("Table so far");
    }

    @Test
    void generate_nullGoalTimeline_producesNoDramaLines() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, null)),
                standings());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Comeback");
        assertThat(recap).doesNotContain("Late drama");
    }

    @Test
    void generate_awayTimelineThatDoesNotMatchTheScore_isIgnored() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 3, List.of(
                        "Alpha, Petar, minute 5, first half",
                        "Beta, Ivan, minute 18, second half"))),
                standings());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Comeback");
        assertThat(recap).doesNotContain("Late drama");
    }

    @Test
    void generate_singleGoalDeficit_isNotAComeback() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of(
                        "Beta, Ivan, minute 5, first half",
                        "Alpha, Petar, minute 3, second half",
                        "Alpha, Georgi, minute 9, second half"))),
                standings());

        assertThat(client.generate(data)).doesNotContain("Comeback");
    }

    @Test
    void generate_seasonInProgress_doesNotCrownAChampion() {
        String recap = client.generate(roundData("en", 0));

        assertThat(recap).contains("season review");
        assertThat(recap).contains("Table so far");
        assertThat(recap).contains("Leader: Alpha with 7 points");
        assertThat(recap).doesNotContain("Champion");
        assertThat(recap).doesNotContain("Final table");
        assertThat(recap).contains("Bottom of the table: Delta with 1 points");
    }

    @Test
    void generate_seasonRecap_omitsTheFullResultList() {
        String recap = client.generate(roundData("en", 0));

        assertThat(recap).doesNotContain("Results:");
        assertThat(recap).doesNotContain("Alpha 5:0 Beta");
        assertThat(recap.split("\n\n")).hasSize(3);
    }

    @Test
    void generate_roundRecap_keepsTheFullResultList() {
        String recap = client.generate(roundData("en", 3));

        assertThat(recap).contains("Results:");
        assertThat(recap).contains("Alpha 5:0 Beta");
        assertThat(recap.split("\n\n")).hasSize(3);
    }

    @Test
    void generate_seasonRecap_countsHighScoringMatches() {
        String recap = client.generate(roundData("en", 0));

        assertThat(recap).contains("Matches with five goals or more: 2");
    }

    @Test
    void generate_seasonRecap_noHighScoringMatches_omitsTheCount() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of())),
                standings());

        assertThat(client.generate(data)).doesNotContain("Matches with five goals or more");
    }

    @Test
    void generate_roundRecap_omitsTheHighScoringCount() {
        assertThat(client.generate(roundData("en", 3)))
                .doesNotContain("Matches with five goals or more");
    }

    @Test
    void generate_reportsGoallessDrawsAndCleanSheets() {
        String recap = client.generate(roundData("en", 3));

        assertThat(recap).contains("Goalless draws: 1");
        assertThat(recap).contains("Matches with a clean sheet: 2");
    }

    @Test
    void generate_reportsBestAwayPerformance() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 1, 4, List.of())),
                standings());

        assertThat(client.generate(data)).contains("Best away display: Beta won 4:1 at Alpha");
    }

    @Test
    void generate_noAwayWin_omitsTheAwayHighlight() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 3, 0, List.of())),
                standings());

        assertThat(client.generate(data)).doesNotContain("Best away display");
    }

    @Test
    void generate_securedTitle_usesChampionWordingAndFinalTable() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                matches(), championStandings());

        String recap = client.generate(data);

        assertThat(recap).contains("Final table");
        assertThat(recap).contains("Champion: Alpha with 7 points");
        assertThat(recap).doesNotContain("Leader:");
    }

    @Test
    void generate_securedTitleMidRound_stillOmitsTheStandings() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 5, "en", "English",
                matches(), championStandings());

        assertThat(client.generate(data)).doesNotContain("Champion: Alpha");
    }

    @Test
    void generate_reportsBestAttackAndDefence() {
        String recap = client.generate(roundData("en", 0));

        assertThat(recap).contains("Best attack: Alpha with 9 goals scored");
        assertThat(recap).contains("Best defence: Alpha with 2 goals conceded");
    }

    @Test
    void generate_ignoresTeamsWithoutPlayedMatches() {
        List<RoundRecapStandingData> standings = new ArrayList<>(standings());
        standings.add(new RoundRecapStandingData(5, "Unplayed", 0, 0, 0, 0, 0, 0, 0, 0, false));

        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                matches(), standings);

        assertThat(client.generate(data)).doesNotContain("Unplayed");
    }

    @Test
    void generate_noStandings_stillProducesResults() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 2, "en", "English",
                matches(), null);

        String recap = client.generate(data);

        assertThat(recap).contains("Alpha 5:0 Beta");
        assertThat(recap).doesNotContain("Leader");
    }

    @Test
    void generate_allDraws_omitsBiggestWin() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 0, 0, List.of())),
                List.of());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Biggest win");
        assertThat(recap).contains("Draws: 1");
    }

    @Test
    void generate_singleTeamStandings_omitsRunnerUp() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of())),
                List.of(new RoundRecapStandingData(1, "Alpha", 1, 1, 0, 0, 2, 1, 1, 3, false)));

        assertThat(client.generate(data)).doesNotContain("Runner-up");
    }

    @Test
    void generate_noDraws_omitsTheDrawLine() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(new RoundRecapMatchData("Alpha", "Beta", 3, 1, List.of())),
                standings());

        String recap = client.generate(data);

        assertThat(recap).doesNotContain("Draws:");
        assertThat(recap).contains("Biggest win");
    }

    @Test
    void generate_seasonWithTwoTeams_omitsBottomOfTable() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 0, "en", "English",
                matches(),
                List.of(standings().get(0), standings().get(1)));

        String recap = client.generate(data);

        assertThat(recap).contains("Leader: Alpha");
        assertThat(recap).doesNotContain("Bottom of the table");
    }

    @Test
    void generate_nullData_throws() {
        assertThatThrownBy(() -> client.generate(null))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("No data");
    }

    @Test
    void generate_noMatches_throws() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                List.of(), standings());

        assertThatThrownBy(() -> client.generate(data))
                .isInstanceOf(RoundRecapGenerationException.class)
                .hasMessageContaining("no played matches");
    }

    @Test
    void generate_nullMatches_throws() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, "en", "English",
                null, standings());

        assertThatThrownBy(() -> client.generate(data))
                .isInstanceOf(RoundRecapGenerationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "bg", "de"})
    void generate_everyLocale_resolvesEveryKey(String locale) {
        String recap = client.generate(roundData(locale, 0));

        assertThat(recap).doesNotContain("recap.");
        assertThat(recap).contains("Alpha");
        assertThat(recap.split("\n\n")).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "bg", "de"})
    void generate_nullLocaleTag_fallsBackWithoutFailing(String ignored) {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", 1, null, null,
                matches(), standings());

        assertThat(client.generate(data)).doesNotContain("recap.");
    }

    @Test
    void generate_isDeterministic() {
        assertThat(client.generate(roundData("bg", 4)))
                .isEqualTo(client.generate(roundData("bg", 4)));
    }

    private RoundRecapPromptData roundData(String locale, int round) {
        return new RoundRecapPromptData("Test League", round, locale, "English", matches(), standings());
    }

    private List<RoundRecapMatchData> matches() {
        return Arrays.asList(
                new RoundRecapMatchData("Alpha", "Beta", 5, 0, List.of()),
                new RoundRecapMatchData("Gama", "Delta", 3, 3, List.of()),
                new RoundRecapMatchData("Beta", "Gama", 0, 0, List.of()));
    }

    private List<RoundRecapStandingData> championStandings() {
        List<RoundRecapStandingData> rows = new ArrayList<>(standings());
        RoundRecapStandingData leader = rows.get(0);
        rows.set(0, new RoundRecapStandingData(leader.position(), leader.team(), leader.played(),
                leader.wins(), leader.draws(), leader.losses(), leader.goalsFor(),
                leader.goalsAgainst(), leader.goalDifference(), leader.points(), true));
        return rows;
    }

    private List<RoundRecapStandingData> standings() {
        return List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 2, 7, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 1, 1, 4, 6, -2, 4, false),
                new RoundRecapStandingData(3, "Gama", 3, 1, 0, 2, 5, 7, -2, 3, false),
                new RoundRecapStandingData(4, "Delta", 3, 0, 1, 2, 3, 6, -3, 1, false));
    }
}
