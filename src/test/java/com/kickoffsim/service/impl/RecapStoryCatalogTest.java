package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPlayerData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecapStoryCatalogTest {

    private static final int SEASON = 0;

    private RecapStoryCatalog catalog;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        catalog = new RecapStoryCatalog(messageSource);
    }

    @Test
    void roundStories_comebackWin_isReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 3, 2,
                "Beta, Ivan, minute 5, first half",
                "Beta, Ivan, minute 12, first half",
                "Alpha, Petar, minute 3, second half",
                "Alpha, Petar, minute 9, second half",
                "Alpha, Georgi, minute 14, second half"));

        assertThat(body(stories, RecapStoryKind.COMEBACK))
                .isEqualTo("Alpha trailed Beta by 2 goals and still won it 3:2.");
    }

    @Test
    void roundStories_comebackToADraw_usesTheRescueWording() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 2,
                "Beta, Ivan, minute 5, first half",
                "Beta, Ivan, minute 12, first half",
                "Alpha, Petar, minute 3, second half",
                "Alpha, Georgi, minute 9, second half"));

        assertThat(body(stories, RecapStoryKind.COMEBACK))
                .isEqualTo("Alpha were 2 goals down against Beta and salvaged 2:2.");
    }

    @Test
    void roundStories_awayComeback_namesTheVisitors() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 3,
                "Alpha, Petar, minute 3, first half",
                "Alpha, Petar, minute 8, first half",
                "Beta, Ivan, minute 2, second half",
                "Beta, Ivan, minute 7, second half",
                "Beta, Stefan, minute 11, second half"));

        assertThat(body(stories, RecapStoryKind.COMEBACK)).startsWith("Beta trailed Alpha by 2 goals");
    }

    @Test
    void roundStories_awayComebackToADraw_namesTheVisitors() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 2,
                "Alpha, Petar, minute 3, first half",
                "Alpha, Petar, minute 8, first half",
                "Beta, Ivan, minute 2, second half",
                "Beta, Stefan, minute 11, second half"));

        assertThat(body(stories, RecapStoryKind.COMEBACK)).startsWith("Beta were 2 goals down against Alpha");
    }

    @Test
    void roundStories_twoComebacks_prefersTheOneThatEndedInAWin() {
        List<RecapStory> stories = round(
                match("Alpha", "Beta", 2, 2,
                        "Beta, Ivan, minute 3, first half",
                        "Beta, Ivan, minute 8, first half",
                        "Alpha, Petar, minute 2, second half",
                        "Alpha, Georgi, minute 7, second half"),
                match("Gama", "Delta", 3, 2,
                        "Delta, Nikola, minute 4, first half",
                        "Delta, Nikola, minute 9, first half",
                        "Gama, Todor, minute 1, second half",
                        "Gama, Todor, minute 6, second half",
                        "Gama, Mitko, minute 12, second half"));

        assertThat(body(stories, RecapStoryKind.COMEBACK)).startsWith("Gama trailed Delta");
    }

    @Test
    void roundStories_singleGoalDeficit_isNotAComeback() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 1,
                "Beta, Ivan, minute 5, first half",
                "Alpha, Petar, minute 3, second half",
                "Alpha, Georgi, minute 9, second half"));

        assertThat(find(stories, RecapStoryKind.COMEBACK)).isEmpty();
    }

    @Test
    void roundStories_lateWinner_isReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 1,
                "Alpha, Petar, minute 10, first half",
                "Beta, Ivan, minute 4, second half",
                "Alpha, Georgi, minute 18, second half"));

        assertThat(body(stories, RecapStoryKind.LATE_DRAMA))
                .isEqualTo("Alpha decided the game against Beta in minute 18 of the second half to make it 2:1.");
    }

    @Test
    void roundStories_lateEqualiser_isReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 1,
                "Alpha, Petar, minute 10, first half",
                "Beta, Ivan, minute 20, second half"));

        assertThat(body(stories, RecapStoryKind.LATE_DRAMA))
                .isEqualTo("Beta levelled against Alpha in minute 20 of the second half for 1:1.");
    }

    @Test
    void roundStories_lateGoalThatChangesNothing_isNotReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 0,
                "Alpha, Petar, minute 10, first half",
                "Alpha, Georgi, minute 17, second half"));

        assertThat(find(stories, RecapStoryKind.LATE_DRAMA)).isEmpty();
    }

    @Test
    void roundStories_lateGoalInTheFirstHalf_isNotReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 1,
                "Alpha, Petar, minute 3, first half",
                "Beta, Ivan, minute 19, first half"));

        assertThat(find(stories, RecapStoryKind.LATE_DRAMA)).isEmpty();
    }

    @Test
    void roundStories_lateGoalWithoutARecordedMinute_isNotReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 1,
                "Alpha, Petar, minute 10, first half",
                "Beta, Ivan, minute not recorded, second half"));

        assertThat(find(stories, RecapStoryKind.LATE_DRAMA)).isEmpty();
    }

    @Test
    void roundStories_swings_listTheProgression() {
        List<RecapStory> stories = round(swingMatch());

        assertThat(body(stories, RecapStoryKind.SWINGS)).isEqualTo(
                "Alpha 4:5 Beta — the lead changed 3 times. "
                        + "Goal by goal: 1:0, 1:1, 1:2, 2:2, 3:2, 3:3, 3:4, 4:4, 4:5.");
    }

    @Test
    void roundStories_notEnoughLeadChanges_isNotReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 1,
                "Alpha, Petar, minute 3, first half",
                "Beta, Ivan, minute 6, first half",
                "Alpha, Georgi, minute 9, first half"));

        assertThat(find(stories, RecapStoryKind.SWINGS)).isEmpty();
    }

    @Test
    void roundStories_timelineThatDoesNotMatchTheScore_isIgnored() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 3, 2,
                "Beta, Ivan, minute 5, first half",
                "Alpha, Petar, minute 18, second half"));

        assertThat(find(stories, RecapStoryKind.COMEBACK)).isEmpty();
        assertThat(find(stories, RecapStoryKind.LATE_DRAMA)).isEmpty();
    }

    @Test
    void roundStories_awayTimelineThatDoesNotMatchTheScore_isIgnored() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 3,
                "Alpha, Petar, minute 5, first half",
                "Beta, Ivan, minute 18, second half"));

        assertThat(find(stories, RecapStoryKind.COMEBACK)).isEmpty();
    }

    @Test
    void roundStories_nullTimeline_producesNoDrama() {
        List<RecapStory> stories = round(new RoundRecapMatchData("Alpha", "Beta", 2, 1, null));

        assertThat(find(stories, RecapStoryKind.COMEBACK)).isEmpty();
        assertThat(find(stories, RecapStoryKind.MVP)).isEmpty();
    }

    @Test
    void roundStories_threeGoalsForOnePlayer_isAHatTrick() {
        List<RecapStory> stories = round(swingMatch());

        assertThat(body(stories, RecapStoryKind.HAT_TRICK))
                .isEqualTo("Petar scored 3 for Alpha.");
        assertThat(find(stories, RecapStoryKind.MVP)).isEmpty();
    }

    @Test
    void roundStories_belowAHatTrick_namesAStarPlayer() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 0,
                "Alpha, Petar, minute 3, first half"));

        assertThat(body(stories, RecapStoryKind.MVP))
                .isEqualTo("Petar of Alpha finished the round on one goal.");
    }

    @Test
    void roundStories_starPlayerWithOneAssist_usesSingularWording() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 0,
                "Alpha, Petar, minute 3, first half, assist Georgi",
                "Alpha, Georgi, minute 9, first half, assist Petar"));

        assertThat(body(stories, RecapStoryKind.MVP))
                .isEqualTo("Georgi of Alpha finished the round on one goal and one assist.");
    }

    @Test
    void roundStories_starPlayerWithSeveralAssists_usesPluralWording() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 4, 0,
                "Alpha, Star, minute 3, first half",
                "Alpha, Asen, minute 6, first half, assist Star",
                "Alpha, Boris, minute 9, first half, assist Star",
                "Alpha, Chavdar, minute 12, first half, assist Star"));

        assertThat(body(stories, RecapStoryKind.MVP))
                .isEqualTo("Star of Alpha finished the round on one goal and 3 assists.");
    }

    @Test
    void roundStories_ownGoals_doNotCountForThePlayer() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 0,
                "Alpha, Petar, minute 3, first half",
                "Alpha, Ivan, minute 9, first half, own goal"));

        assertThat(body(stories, RecapStoryKind.MVP)).startsWith("Petar of Alpha");
    }

    @Test
    void roundStories_assists_countTowardsTheTeamOfTheRound() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 0,
                "Alpha, Petar, minute 3, first half, assist Georgi"));

        assertThat(items(stories, RecapStoryKind.SQUAD))
                .containsExactly("Petar::Alpha::1::0", "Georgi::Alpha::0::1");
    }

    @Test
    void roundStories_assistFollowedByAnotherField_readsOnlyTheName() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 0,
                "Alpha, Petar, minute 3, first half, assist Georgi, penalty"));

        assertThat(items(stories, RecapStoryKind.SQUAD)).contains("Georgi::Alpha::0::1");
    }

    @Test
    void roundStories_goalWithoutSeparatedFields_isSkipped() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 0,
                "Alpha, Petar, minute 3, first half",
                "unstructured"));

        assertThat(items(stories, RecapStoryKind.SQUAD)).containsExactly("Petar::Alpha::1::0");
    }

    @Test
    void roundStories_moreThanSixContributors_fillTheBench() {
        List<RecapStory> stories = round(
                match("Alpha", "Beta", 4, 0,
                        "Alpha, Asen, minute 3, first half",
                        "Alpha, Boris, minute 6, first half",
                        "Alpha, Chavdar, minute 9, first half",
                        "Alpha, Dimitar, minute 12, first half"),
                match("Gama", "Delta", 4, 0,
                        "Gama, Emil, minute 3, first half",
                        "Gama, Filip, minute 6, first half",
                        "Gama, Georgi, minute 9, first half",
                        "Gama, Hristo, minute 12, first half"));

        assertThat(items(stories, RecapStoryKind.SQUAD)).hasSize(6);
        assertThat(items(stories, RecapStoryKind.BENCH)).containsExactly(
                "Georgi::Gama::1::0", "Hristo::Gama::1::0");
    }

    @Test
    void roundStories_noGoals_haveNoSquadAtAll() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 0, 0));

        assertThat(find(stories, RecapStoryKind.SQUAD)).isEmpty();
        assertThat(find(stories, RecapStoryKind.BENCH)).isEmpty();
    }

    @Test
    void roundStories_alwaysListTheResults() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 5, 0), match("Gama", "Delta", 3, 3));

        assertThat(items(stories, RecapStoryKind.RESULTS))
                .containsExactly("Alpha 5:0 Beta", "Gama 3:3 Delta");
    }

    @Test
    void roundStories_knownMatches_carryTheirIdIntoTheResults() {
        List<RecapStory> stories = round(
                new RoundRecapMatchData("Alpha", "Beta", 2, 1, List.of(), "match-1"));

        assertThat(items(stories, RecapStoryKind.RESULTS)).containsExactly("Alpha 2:1 Beta::match-1");
    }

    @Test
    void roundStories_statsChips_countEverythingThatHappened() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 5, 0),
                match("Gama", "Delta", 3, 3), match("Beta", "Gama", 0, 0));

        assertThat(items(stories, RecapStoryKind.STATS)).containsExactly(
                "3::matches", "11::goals", "3.7::goals per game", "2::draws", "2::clean sheets",
                "1::goalless", "2::five-goal games");
    }

    @Test
    void roundStories_nothingRemarkable_leavesTheOptionalChipsOut() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 2, 1));

        assertThat(items(stories, RecapStoryKind.STATS))
                .containsExactly("1::match", "3::goals", "3.0::goals per game");
    }

    @Test
    void roundStories_biggestWinAndGoalFest_areReported() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 5, 0), match("Gama", "Delta", 3, 3));

        assertThat(body(stories, RecapStoryKind.BIG_WIN))
                .isEqualTo("Alpha beat Beta 5:0, the widest margin anywhere at 5 goals.");
        assertThat(body(stories, RecapStoryKind.GOAL_FEST))
                .isEqualTo("Gama against Delta produced 6 goals and finished 3:3.");
    }

    @Test
    void roundStories_onlyDraws_haveNoBigWin() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 0, 0));

        assertThat(find(stories, RecapStoryKind.BIG_WIN)).isEmpty();
        assertThat(find(stories, RecapStoryKind.GOAL_FEST)).isEmpty();
    }

    @Test
    void roundStories_awayWin_namesTheVisitors() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 5, 0), match("Gama", "Delta", 1, 4));

        assertThat(body(stories, RecapStoryKind.AWAY_WIN))
                .isEqualTo("Delta went to Gama and came away with a 4:1 win.");
    }

    @Test
    void roundStories_oneMatch_isNotToldTwice() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 1, 6));

        assertThat(body(stories, RecapStoryKind.BIG_WIN)).startsWith("Beta beat Alpha 6:1");
        assertThat(find(stories, RecapStoryKind.AWAY_WIN)).isEmpty();
        assertThat(find(stories, RecapStoryKind.GOAL_FEST)).isEmpty();
    }

    @Test
    void roundStories_noAwayWin_hasNoAwayStory() {
        List<RecapStory> stories = round(match("Alpha", "Beta", 3, 0));

        assertThat(find(stories, RecapStoryKind.AWAY_WIN)).isEmpty();
    }

    @Test
    void seasonStories_securedTitleWithNoGamesLeft_leadsWithTheTitle() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), championTable(), 3), Locale.ENGLISH);

        RecapStory title = find(stories, RecapStoryKind.TITLE_DECIDED).orElseThrow();
        assertThat(title.weight()).isEqualTo(90);
        assertThat(title.body()).startsWith("Alpha finish top with 7 points");
        assertThat(find(stories, RecapStoryKind.TITLE_RACE)).isEmpty();
    }

    @Test
    void seasonStories_titleSharedOnPoints_isToldAsAGoalDifferenceStory() {
        List<RoundRecapStandingData> rows = new ArrayList<>(championTable());
        rows.set(1, new RoundRecapStandingData(2, "Beta", 3, 2, 1, 0, 5, 4, 1, 7, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        RecapStory title = find(stories, RecapStoryKind.TITLE_DECIDED).orElseThrow();
        assertThat(title.weight()).isEqualTo(90);
        assertThat(title.body()).contains("goal difference").contains("Alpha");
        assertThat(find(stories, RecapStoryKind.TITLE_RACE)).isEmpty();
    }

    @Test
    void seasonStories_biggestWinAndGoalFest_areToldAsSeasonRecords() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), table(), 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.BIG_WIN)).contains("Alpha", "Beta", "5");
        assertThat(body(stories, RecapStoryKind.GOAL_FEST)).contains("Gama", "Delta", "6");
        assertThat(find(stories, RecapStoryKind.AWAY_WIN)).isEmpty();
    }

    @Test
    void seasonStories_singleGoallessGame_hasNoRecords() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, List.of(match("Alpha", "Beta", 0, 0)), table(), 3), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.BIG_WIN)).isEmpty();
        assertThat(find(stories, RecapStoryKind.GOAL_FEST)).isEmpty();
    }

    @Test
    void seasonStories_singleDecisiveGame_hasARoutButNoGoalFest() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, List.of(match("Alpha", "Beta", 3, 0)), table(), 3), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.BIG_WIN)).isPresent();
        assertThat(find(stories, RecapStoryKind.GOAL_FEST)).isEmpty();
    }

    @Test
    void seasonStories_noMatches_haveNoRecords() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, List.of(), table(), 3), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.BIG_WIN)).isEmpty();
        assertThat(find(stories, RecapStoryKind.GOAL_FEST)).isEmpty();
    }

    @Test
    void seasonStories_secondRichestGameIsGoalless_hasNoGoalFest() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, List.of(match("Alpha", "Beta", 3, 0), match("Gama", "Delta", 0, 0)), table(), 3),
                Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.BIG_WIN)).isPresent();
        assertThat(find(stories, RecapStoryKind.GOAL_FEST)).isEmpty();
    }

    @Test
    void roundStories_titleClinchedThisRound_crownsTheChampion() {
        List<RecapStory> stories = catalog.roundStories(
                clinchData(2, seasonMatches(), championTable(), 2), Locale.ENGLISH);

        RecapStory title = find(stories, RecapStoryKind.TITLE_DECIDED).orElseThrow();
        assertThat(title.weight()).isGreaterThanOrEqualTo(200);
        assertThat(title.body()).contains("Alpha");
    }

    @Test
    void roundStories_titleClinchedThreeWayThisRound_usesTheTiebreakStory() {
        List<RoundRecapStandingData> rows = new ArrayList<>(championTable());
        rows.set(1, new RoundRecapStandingData(2, "Beta", 3, 2, 1, 0, 5, 4, 1, 7, false));

        List<RecapStory> stories = catalog.roundStories(
                clinchData(2, seasonMatches(), rows, 2), Locale.ENGLISH);

        RecapStory title = find(stories, RecapStoryKind.TITLE_DECIDED).orElseThrow();
        assertThat(title.weight()).isGreaterThanOrEqualTo(200);
        assertThat(title.body()).contains("goal difference");
    }

    @Test
    void roundStories_beforeTheClinchRound_hasNoChampionStory() {
        List<RecapStory> stories = catalog.roundStories(
                clinchData(1, seasonMatches(), championTable(), 2), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_DECIDED)).isEmpty();
    }

    @Test
    void roundStories_noClinchRound_hasNoChampionStory() {
        List<RecapStory> stories = catalog.roundStories(
                clinchData(2, seasonMatches(), championTable(), null), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_DECIDED)).isEmpty();
    }

    @Test
    void roundStories_clinchRoundButNoStandings_hasNoChampionStory() {
        List<RecapStory> stories = catalog.roundStories(
                clinchData(2, seasonMatches(), List.of(), 2), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_DECIDED)).isEmpty();
    }

    @Test
    void roundStories_clinchRoundButLeaderNotChampion_hasNoChampionStory() {
        List<RecapStory> stories = catalog.roundStories(
                clinchData(2, seasonMatches(), table(), 2), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_DECIDED)).isEmpty();
    }

    @Test
    void seasonStories_securedTitleWithGamesLeft_stepsAsideForTheOtherRaces() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), championTable(), 10), Locale.ENGLISH);

        RecapStory title = find(stories, RecapStoryKind.TITLE_DECIDED).orElseThrow();
        RecapStory second = find(stories, RecapStoryKind.SECOND_PLACE).orElseThrow();
        assertThat(title.weight()).isEqualTo(40);
        assertThat(second.weight()).isEqualTo(60);
        assertThat(second.weight()).isGreaterThan(title.weight());
    }

    @Test
    void seasonStories_openTitle_describesTheRace() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), table(), 10), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.TITLE_RACE)).isEqualTo(
                "Alpha lead on 7 points, but 4 sides are still in the hunt "
                        + "with 7 rounds to play — this is going to the wire.");
    }

    @Test
    void seasonStories_twoTeamRaceWithOneRoundLeft_describesTheRunIn() {
        List<RoundRecapStandingData> rows = List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 3, 1, 0, 9, 2, 7, 10, false),
                new RoundRecapStandingData(2, "Beta", 3, 2, 2, 0, 6, 3, 3, 8, false),
                new RoundRecapStandingData(3, "Gama", 3, 0, 2, 1, 2, 6, -4, 2, false),
                new RoundRecapStandingData(4, "Delta", 3, 0, 1, 2, 1, 7, -6, 1, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 4), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.TITLE_RACE)).isEqualTo(
                "Alpha top the table on 10 points, 2 clear of Beta on 8. "
                        + "With one game left for the leaders, the title is still open.");
    }

    @Test
    void seasonStories_twoTeamRaceWithGamesInHand_hasNoRunInBoost() {
        List<RoundRecapStandingData> rows = List.of(
                new RoundRecapStandingData(1, "Alpha", 10, 9, 1, 0, 30, 8, 22, 28, false),
                new RoundRecapStandingData(2, "Beta", 10, 8, 2, 0, 26, 10, 16, 26, false),
                new RoundRecapStandingData(3, "Gama", 10, 3, 1, 6, 12, 20, -8, 10, false),
                new RoundRecapStandingData(4, "Delta", 10, 1, 2, 7, 8, 28, -20, 5, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 14), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.TITLE_RACE)).isEqualTo(
                "Alpha top the table on 28 points, 2 clear of Beta on 26. "
                        + "With 4 games left for the leaders, the title is still open.");
    }

    @Test
    void seasonStories_uncatchableLeader_isFramedAsCommanding() {
        List<RoundRecapStandingData> rows = List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 3, 0, 0, 9, 1, 8, 9, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 0, 2, 3, 5, -2, 3, false),
                new RoundRecapStandingData(3, "Gama", 3, 1, 0, 2, 2, 5, -3, 3, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 4), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.TITLE_RACE)).isEqualTo(
                "Alpha lead on 9 points, 6 points clear of Beta with one round left — the title is all but theirs.");
    }

    @Test
    void seasonStories_singleTeamInTheTable_hasNoTitleRace() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), List.of(table().get(0)), 10), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_RACE)).isEmpty();
        assertThat(find(stories, RecapStoryKind.SECOND_PLACE)).isEmpty();
        assertThat(find(stories, RecapStoryKind.BOTTOM)).isEmpty();
    }

    @Test
    void seasonStories_nobodyHasPlayed_hasNoTableStories() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(),
                        List.of(new RoundRecapStandingData(1, "Alpha", 0, 0, 0, 0, 0, 0, 0, 0, false)), 10),
                Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_RACE)).isEmpty();
        assertThat(find(stories, RecapStoryKind.ATTACK_DEFENCE)).isEmpty();
    }

    @Test
    void seasonStories_nullTable_hasNoTableStories() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), null, 10), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.TITLE_DECIDED)).isEmpty();
        assertThat(find(stories, RecapStoryKind.TITLE_RACE)).isEmpty();
    }

    @Test
    void seasonStories_secondPlaceAndBottom_areReported() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), table(), 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.SECOND_PLACE))
                .isEqualTo("Beta have second sewn up on 4 points, "
                        + "one point ahead of Gama with too few games left for a challenge.");
        assertThat(body(stories, RecapStoryKind.BOTTOM))
                .isEqualTo("Bottom of the table — Delta, 1 points from 3 matches.");
    }

    @Test
    void seasonStories_teamsLevelAtTheBottom_areNamedTogether() {
        List<RoundRecapStandingData> rows = new ArrayList<>(table());
        rows.set(2, new RoundRecapStandingData(3, "Gama", 3, 0, 1, 2, 5, 7, -2, 1, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.BOTTOM))
                .isEqualTo("Bottom of the table — Gama and Delta, 1 points from 3 matches.");
    }

    @Test
    void seasonStories_oneTeamLeadsBothCharts_isNotNamedTwice() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), table(), 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.ATTACK_DEFENCE))
                .isEqualTo("Alpha lead both charts — 9 goals scored and only 2 conceded.");
    }

    @Test
    void seasonStories_differentAttackAndDefence_nameBoth() {
        List<RoundRecapStandingData> rows = List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 5, 4, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 1, 1, 4, 2, 2, 4, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.ATTACK_DEFENCE))
                .isEqualTo("Best attack — Alpha, 9 scored. Best defence — Beta, 2 conceded.");
    }

    @Test
    void seasonStories_singleAttackLeaderButTiedDefence_namesBothSides() {
        List<RoundRecapStandingData> rows = List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 4, 5, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 1, 1, 4, 4, 0, 4, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.ATTACK_DEFENCE))
                .isEqualTo("Best attack — Alpha, 9 scored. Best defence — Alpha and Beta, 4 conceded.");
    }

    @Test
    void seasonStories_tiedChartsListEveryTeam() {
        List<RoundRecapStandingData> rows = List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 5, 4, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 2, 1, 0, 9, 5, 4, 7, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.ATTACK_DEFENCE))
                .isEqualTo("Best attack — Alpha and Beta, 9 scored. "
                        + "Best defence — Alpha and Beta, 5 conceded.");
    }

    @Test
    void seasonStories_leadersLevelOnPoints_shareTheTitleRace() {
        List<RoundRecapStandingData> rows = new ArrayList<>(table());
        rows.set(1, new RoundRecapStandingData(2, "Beta", 3, 2, 1, 0, 4, 6, -2, 7, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 10), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.TITLE_RACE))
                .isEqualTo("Alpha and Beta are level on 7 points with 7 games each still to play.");
    }

    @Test
    void seasonStories_secondAndThirdLevelOnPoints_shareTheSpot() {
        List<RoundRecapStandingData> rows = new ArrayList<>(table());
        rows.set(2, new RoundRecapStandingData(3, "Gama", 3, 1, 1, 1, 5, 7, -2, 4, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        assertThat(body(stories, RecapStoryKind.SECOND_PLACE))
                .isEqualTo("Beta and Gama are level on 4 points in the race for second.");
    }

    @Test
    void seasonStories_scorersLevelOnGoals_shareTheChart() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", SEASON, "en", "English",
                seasonMatches(), table(), 3,
                List.of(new RoundRecapPlayerData("Petar", "Alpha", 9),
                        new RoundRecapPlayerData("Ivan", "Beta", 9),
                        new RoundRecapPlayerData("Georgi", "Gama", 4)),
                List.of(), null);

        assertThat(body(catalog.seasonStories(data, Locale.ENGLISH), RecapStoryKind.SCORER_RACE))
                .isEqualTo("Top of the charts — Petar and Ivan, level on 9 goals.");
    }

    @Test
    void seasonStories_threeWinsInARow_areAStreak() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, List.of(
                        match("Alpha", "Beta", 1, 0),
                        match("Gama", "Alpha", 0, 2),
                        match("Alpha", "Delta", 3, 1)), table(), 10), Locale.ENGLISH);

        RecapStory streak = find(stories, RecapStoryKind.STREAK).orElseThrow();
        assertThat(streak.body()).isEqualTo("Alpha have won their last 3 matches.");
        assertThat(streak.weight()).isEqualTo(75);
    }

    @Test
    void seasonStories_aLossBreaksTheStreak() {
        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, List.of(
                        match("Alpha", "Beta", 1, 0),
                        match("Gama", "Alpha", 2, 0),
                        match("Alpha", "Delta", 3, 1)), table(), 10), Locale.ENGLISH);

        assertThat(find(stories, RecapStoryKind.STREAK)).isEmpty();
    }

    @Test
    void seasonStories_scorerRaceWithAChaser_namesBoth() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", SEASON, "en", "English",
                seasonMatches(), table(), 3,
                List.of(new RoundRecapPlayerData("Petar", "Alpha", 9),
                        new RoundRecapPlayerData("Ivan", "Beta", 6)),
                List.of(), null);

        List<RecapStory> stories = catalog.seasonStories(data, Locale.ENGLISH);

        RecapStory race = find(stories, RecapStoryKind.SCORER_RACE).orElseThrow();
        assertThat(race.body()).isEqualTo("Petar of Alpha tops the charts with 9 goals, ahead of Ivan on 6.");
        assertThat(race.weight()).isEqualTo(38);
    }

    @Test
    void seasonStories_singleScorer_dropsTheChaserClause() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", SEASON, "en", "English",
                seasonMatches(), table(), 3,
                List.of(new RoundRecapPlayerData("Petar", "Alpha", 4)), List.of(), null);

        assertThat(body(catalog.seasonStories(data, Locale.ENGLISH), RecapStoryKind.SCORER_RACE))
                .isEqualTo("Petar of Alpha tops the charts with 4 goals, with nobody else on the sheet yet.");
    }

    @Test
    void seasonStories_noScorers_haveNoScorerRace() {
        assertThat(find(catalog.seasonStories(
                data(SEASON, seasonMatches(), table(), 3), Locale.ENGLISH), RecapStoryKind.SCORER_RACE))
                .isEmpty();
    }

    @Test
    void seasonStories_nullScorers_haveNoScorerRace() {
        RoundRecapPromptData data = new RoundRecapPromptData("Test League", SEASON, "en", "English",
                seasonMatches(), table(), 3, null, null, null);

        assertThat(find(catalog.seasonStories(data, Locale.ENGLISH), RecapStoryKind.SCORER_RACE)).isEmpty();
    }

    @Test
    void seasonStories_teamsWithoutPlayedMatches_areIgnored() {
        List<RoundRecapStandingData> rows = new ArrayList<>(table());
        rows.add(new RoundRecapStandingData(5, "Unplayed", 0, 0, 0, 0, 0, 0, 0, 0, false));

        List<RecapStory> stories = catalog.seasonStories(
                data(SEASON, seasonMatches(), rows, 3), Locale.ENGLISH);

        assertThat(stories).noneMatch(story -> story.body().contains("Unplayed"));
    }

    @Test
    void headlines_areStableForTheSameInput() {
        assertThat(round(swingMatch())).isEqualTo(round(swingMatch()));
    }

    @Test
    void headlines_varyBetweenLeaguesAndRounds() {
        Set<String> headlines = new LinkedHashSet<>();
        for (String league : List.of("Alpha Cup", "Beta Cup", "Gama Cup", "Delta Cup", "Epsilon Cup")) {
            for (int round = 1; round <= 3; round++) {
                RoundRecapPromptData data = new RoundRecapPromptData(league, round, "en", "English",
                        List.of(match("Alpha", "Beta", 5, 0), match("Gama", "Delta", 1, 4)),
                        table(), 3, List.of(), List.of(), null);
                headlines.add(headline(catalog.roundStories(data, Locale.ENGLISH), RecapStoryKind.AWAY_WIN));
            }
        }

        assertThat(headlines).hasSizeGreaterThan(1);
    }

    private List<RecapStory> round(RoundRecapMatchData... matches) {
        return catalog.roundStories(
                data(2, Arrays.asList(matches), table(), 3), Locale.ENGLISH);
    }

    private RoundRecapPromptData data(int round, List<RoundRecapMatchData> matches,
                                      List<RoundRecapStandingData> standings, int matchesPerTeam) {
        return new RoundRecapPromptData("Test League", round, "en", "English",
                matches, standings, matchesPerTeam, List.of(), List.of(), null);
    }

    private RoundRecapPromptData clinchData(int round, List<RoundRecapMatchData> matches,
                                            List<RoundRecapStandingData> standings, Integer clinchRound) {
        return new RoundRecapPromptData("Test League", round, "en", "English",
                matches, standings, 3, List.of(), List.of(), clinchRound);
    }

    private RoundRecapMatchData match(String home, String away, int homeScore, int awayScore, String... goals) {
        return new RoundRecapMatchData(home, away, homeScore, awayScore, List.of(goals));
    }

    private RoundRecapMatchData swingMatch() {
        return match("Alpha", "Beta", 4, 5,
                "Alpha, Petar, minute 3, first half",
                "Beta, Ivan, minute 6, first half",
                "Beta, Ivan, minute 9, first half",
                "Alpha, Petar, minute 12, first half",
                "Alpha, Petar, minute 2, second half",
                "Beta, Dimitar, minute 5, second half",
                "Beta, Dimitar, minute 8, second half",
                "Alpha, Georgi, minute 11, second half",
                "Beta, Stefan, minute 15, second half");
    }

    private List<RoundRecapMatchData> seasonMatches() {
        return List.of(
                match("Alpha", "Beta", 5, 0),
                match("Gama", "Delta", 3, 3),
                match("Beta", "Gama", 0, 0));
    }

    private List<RoundRecapStandingData> table() {
        return List.of(
                new RoundRecapStandingData(1, "Alpha", 3, 2, 1, 0, 9, 2, 7, 7, false),
                new RoundRecapStandingData(2, "Beta", 3, 1, 1, 1, 4, 6, -2, 4, false),
                new RoundRecapStandingData(3, "Gama", 3, 1, 0, 2, 5, 7, -2, 3, false),
                new RoundRecapStandingData(4, "Delta", 3, 0, 1, 2, 3, 6, -3, 1, false));
    }

    private List<RoundRecapStandingData> championTable() {
        List<RoundRecapStandingData> rows = new ArrayList<>(table());
        RoundRecapStandingData leader = rows.get(0);
        rows.set(0, new RoundRecapStandingData(leader.position(), leader.team(), leader.played(),
                leader.wins(), leader.draws(), leader.losses(), leader.goalsFor(),
                leader.goalsAgainst(), leader.goalDifference(), leader.points(), true));
        return rows;
    }

    private Optional<RecapStory> find(List<RecapStory> stories, RecapStoryKind kind) {
        return stories.stream().filter(story -> story.kind() == kind).findFirst();
    }

    private String body(List<RecapStory> stories, RecapStoryKind kind) {
        return find(stories, kind).orElseThrow().body();
    }

    private String headline(List<RecapStory> stories, RecapStoryKind kind) {
        return find(stories, kind).orElseThrow().headline();
    }

    private List<String> items(List<RecapStory> stories, RecapStoryKind kind) {
        return find(stories, kind).orElseThrow().items();
    }
}
