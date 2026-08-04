package com.kickoffsim.service.impl;

import com.kickoffsim.dto.GoalFact;
import com.kickoffsim.dto.LeagueContext;
import com.kickoffsim.dto.LeagueContext.SurvivalRace;
import com.kickoffsim.dto.LeagueContext.TeamForm;
import com.kickoffsim.dto.LeagueContext.TitleRace;
import com.kickoffsim.dto.MatchFact;
import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapPlayerData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.model.Half;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecapStoryCatalogContextTest {

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
    void contextStories_withoutContext_areEmpty() {
        assertThat(catalog.contextStories(data(List.of(), null, List.of()), java.util.Locale.ENGLISH)).isEmpty();
    }

    @Test
    void contextStories_bottomBeatsTop_isAnUpset() {
        LeagueContext context = context(null, standardForms());
        MatchFact match = new MatchFact("m", 3, "Alpha", "Delta", 0, 2, List.of());

        RecapStory upset = find(catalog.contextStories(
                data(List.of(match), context, List.of()), java.util.Locale.ENGLISH), RecapStoryKind.UPSET);

        assertThat(upset.body()).isEqualTo(
                "Delta (No. 4) beat Alpha (No. 1) 2:0 — the form book went out of the window.");
    }

    @Test
    void contextStories_routOfAStrongSide_outweighsARoutOfAWeakSide() {
        LeagueContext context = context(null, standardForms());
        int statement = weight(catalog.contextStories(
                data(List.of(new MatchFact("m", 3, "Beta", "Alpha", 5, 0, List.of())), context, List.of()),
                java.util.Locale.ENGLISH), RecapStoryKind.BIG_WIN);
        int routine = weight(catalog.contextStories(
                data(List.of(new MatchFact("m", 3, "Beta", "Delta", 5, 0, List.of())), context, List.of()),
                java.util.Locale.ENGLISH), RecapStoryKind.BIG_WIN);

        assertThat(statement).isGreaterThan(routine);
    }

    @Test
    void contextStories_risingWinnerIsASurge() {
        List<TeamForm> forms = List.of(
                new TeamForm("Alpha", 1, 1, 9, 3, "WWW", 3, 3, 0),
                new TeamForm("Beta", 2, 4, 6, 3, "WWW", 3, 3, 0),
                new TeamForm("Gama", 3, 2, 3, 3, "LWL", 0, 0, 1),
                new TeamForm("Delta", 4, 3, 0, 3, "LLL", 0, 0, 3));

        RecapStory surge = find(catalog.contextStories(
                data(List.of(), context(null, forms), List.of()), java.util.Locale.ENGLISH),
                RecapStoryKind.SURGE);

        assertThat(surge.body()).isEqualTo("Beta have won 3 in a row and climbed 2 places to 2.");
    }

    @Test
    void contextStories_fallingWinlessTeamIsACollapse() {
        List<TeamForm> forms = List.of(
                new TeamForm("Alpha", 1, 1, 9, 3, "WWW", 3, 3, 0),
                new TeamForm("Beta", 2, 2, 6, 3, "WWL", 0, 0, 1),
                new TeamForm("Gama", 3, 1, 3, 3, "LLL", 0, 0, 3),
                new TeamForm("Delta", 4, 4, 0, 3, "LLL", 0, 0, 3));

        RecapStory collapse = find(catalog.contextStories(
                data(List.of(), context(null, forms), List.of()), java.util.Locale.ENGLISH),
                RecapStoryKind.COLLAPSE);

        assertThat(collapse.body()).isEqualTo("Gama have gone 3 games without a win and dropped 2 places to 3.");
    }

    @Test
    void contextStories_tightRaceIsATitleBattle() {
        LeagueContext context = context(new TitleRace("Alpha", 9, "Beta", 7, 2, 3, false), standardForms());

        RecapStory battle = find(catalog.contextStories(
                data(List.of(), context, List.of()), java.util.Locale.ENGLISH), RecapStoryKind.TITLE_BATTLE);

        assertThat(battle.body()).contains("Alpha", "Beta").contains("3 rounds");
    }

    @Test
    void contextStories_decidedRaceHasNoTitleBattle() {
        LeagueContext context = context(new TitleRace("Alpha", 20, "Beta", 5, 15, 1, true), standardForms());

        assertThat(catalog.contextStories(data(List.of(), context, List.of()), java.util.Locale.ENGLISH))
                .noneMatch(story -> story.kind() == RecapStoryKind.TITLE_BATTLE);
    }

    @Test
    void contextStories_emergingScorerForASmallSideIsABreakout() {
        MatchFact match = new MatchFact("m", 3, "Delta", "Alpha", 2, 1, List.of(
                new GoalFact(true, "Delta", "Newcomer", null, 10, Half.FIRST, false, false),
                new GoalFact(true, "Delta", "Newcomer", null, 20, Half.SECOND, false, false),
                new GoalFact(false, "Alpha", "Star", null, 30, Half.SECOND, false, false)));
        RoundRecapPromptData data = data(List.of(match), context(null, standardForms()),
                List.of(new RoundRecapPlayerData("Star", "Alpha", 12)));

        RecapStory breakout = find(catalog.contextStories(data, java.util.Locale.ENGLISH),
                RecapStoryKind.BREAKOUT);

        assertThat(breakout.body()).isEqualTo("Newcomer announced themselves with 2 goals for Delta.");
    }

    @Test
    void contextStories_establishedScorerIsNotABreakout() {
        MatchFact match = new MatchFact("m", 3, "Delta", "Alpha", 2, 0, List.of(
                new GoalFact(true, "Delta", "Star", null, 10, Half.FIRST, false, false),
                new GoalFact(true, "Delta", "Star", null, 20, Half.SECOND, false, false)));
        RoundRecapPromptData data = data(List.of(match), context(null, standardForms()),
                List.of(new RoundRecapPlayerData("Star", "Delta", 12)));

        assertThat(catalog.contextStories(data, java.util.Locale.ENGLISH))
                .noneMatch(story -> story.kind() == RecapStoryKind.BREAKOUT);
    }

    @Test
    void contextStories_routWeight_dependsOnOpponentStrength() {
        int routine = weight(rout("Beta", "Delta"), RecapStoryKind.BIG_WIN);
        int statement = weight(rout("Beta", "Alpha"), RecapStoryKind.BIG_WIN);
        int mid = weight(rout("Gama", "Beta"), RecapStoryKind.BIG_WIN);
        int topOverBottom = weight(rout("Alpha", "Delta"), RecapStoryKind.BIG_WIN);

        assertThat(statement).isGreaterThan(mid);
        assertThat(mid).isGreaterThan(routine);
        assertThat(topOverBottom).isEqualTo(mid);
    }

    @Test
    void contextStories_tightRaceButNoRoundsLeft_hasNoTitleBattle() {
        LeagueContext context = context(new TitleRace("Alpha", 9, "Beta", 7, 2, 0, false), standardForms());

        assertThat(catalog.contextStories(data(List.of(), context, List.of()), java.util.Locale.ENGLISH))
                .noneMatch(story -> story.kind() == RecapStoryKind.TITLE_BATTLE);
    }

    @Test
    void contextStories_drawMatch_producesNoUpsetOrRout() {
        MatchFact draw = new MatchFact("m", 3, "Alpha", "Delta", 1, 1, List.of());

        assertThat(catalog.contextStories(
                data(List.of(draw), context(null, standardForms()), List.of()), java.util.Locale.ENGLISH))
                .isEmpty();
    }

    @Test
    void contextStories_shortStreaks_produceNoSurgeOrCollapse() {
        List<TeamForm> forms = List.of(
                new TeamForm("Alpha", 1, 2, 9, 3, "LW", 1, 1, 0),
                new TeamForm("Beta", 2, 1, 6, 3, "WL", 0, 0, 1),
                new TeamForm("Gama", 3, 3, 3, 3, "WLW", 1, 1, 0),
                new TeamForm("Delta", 4, 4, 0, 3, "LLL", 0, 0, 3));

        List<RecapStory> stories = catalog.contextStories(
                data(List.of(), context(null, forms), List.of()), java.util.Locale.ENGLISH);

        assertThat(stories).noneMatch(story -> story.kind() == RecapStoryKind.SURGE);
        assertThat(stories).noneMatch(story -> story.kind() == RecapStoryKind.COLLAPSE);
    }

    @Test
    void contextStories_breakoutWithoutAnEstablishedScorer() {
        MatchFact match = new MatchFact("m", 3, "Delta", "Alpha", 2, 1, List.of(
                goal("Delta", "Newcomer"), goal("Delta", "Newcomer"), goal("Alpha", "Someone")));

        RecapStory breakout = find(catalog.contextStories(
                data(List.of(match), context(null, standardForms()), List.of()), java.util.Locale.ENGLISH),
                RecapStoryKind.BREAKOUT);

        assertThat(breakout.body()).isEqualTo("Newcomer announced themselves with 2 goals for Delta.");
    }

    @Test
    void contextStories_breakoutForARisingSide() {
        List<TeamForm> forms = List.of(
                new TeamForm("Alpha", 1, 1, 9, 3, "WWW", 3, 3, 0),
                new TeamForm("Beta", 2, 4, 6, 3, "WWW", 3, 3, 0),
                new TeamForm("Gama", 3, 2, 3, 3, "LWL", 0, 0, 1),
                new TeamForm("Delta", 4, 3, 0, 3, "LLL", 0, 0, 3));
        MatchFact match = new MatchFact("m", 3, "Beta", "Gama", 2, 0, List.of(
                goal("Beta", "Kid"), goal("Beta", "Kid")));

        assertThat(catalog.contextStories(
                data(List.of(match), context(null, forms),
                        List.of(new RoundRecapPlayerData("Star", "Alpha", 12))), java.util.Locale.ENGLISH))
                .anyMatch(story -> story.kind() == RecapStoryKind.BREAKOUT);
    }

    @Test
    void contextStories_midTableScorerIsNoBreakout() {
        MatchFact match = new MatchFact("m", 3, "Beta", "Gama", 2, 0, List.of(
                goal("Beta", "Kid"), goal("Beta", "Kid")));

        assertThat(catalog.contextStories(
                data(List.of(match), context(null, standardForms()),
                        List.of(new RoundRecapPlayerData("Star", "Alpha", 12))), java.util.Locale.ENGLISH))
                .noneMatch(story -> story.kind() == RecapStoryKind.BREAKOUT);
    }

    @Test
    void contextStories_scorerFromAnUnknownTeamIsNoBreakout() {
        MatchFact match = new MatchFact("m", 3, "Ghost", "Alpha", 2, 1, List.of(
                goal("Ghost", "Kid"), goal("Ghost", "Kid"), goal("Alpha", "Someone")));

        assertThat(catalog.contextStories(
                data(List.of(match), context(null, standardForms()), List.of()), java.util.Locale.ENGLISH))
                .noneMatch(story -> story.kind() == RecapStoryKind.BREAKOUT);
    }

    @Test
    void contextStories_breakoutIgnoresOwnGoalsAndUnknownScorers() {
        MatchFact match = new MatchFact("m", 3, "Delta", "Alpha", 2, 0, List.of(
                new GoalFact(true, "Delta", "OwnScorer", null, 5, Half.FIRST, false, true),
                new GoalFact(true, "Delta", null, null, 10, Half.FIRST, false, false),
                goal("Delta", "Kid"), goal("Delta", "Kid")));

        RecapStory breakout = find(catalog.contextStories(
                data(List.of(match), context(null, standardForms()), null), java.util.Locale.ENGLISH),
                RecapStoryKind.BREAKOUT);

        assertThat(breakout.body()).isEqualTo("Kid announced themselves with 2 goals for Delta.");
    }

    @Test
    void contextStories_twoUpsets_pickTheBiggerShock() {
        List<TeamForm> forms = List.of(
                form("T1", 1, 1), form("T2", 2, 2), form("T3", 3, 3),
                form("T4", 4, 4), form("T5", 5, 5), form("T6", 6, 6));
        List<MatchFact> facts = List.of(
                new MatchFact("a", 3, "T1", "T5", 0, 2, List.of()),
                new MatchFact("b", 3, "T2", "T6", 0, 3, List.of()));

        assertThat(catalog.contextStories(
                data(facts, context(null, forms), List.of()), java.util.Locale.ENGLISH))
                .anyMatch(story -> story.kind() == RecapStoryKind.UPSET);
    }

    @Test
    void contextStories_twoSurgingSides_pickTheHottest() {
        List<TeamForm> forms = List.of(
                new TeamForm("Alpha", 1, 3, 9, 3, "WWW", 3, 3, 0),
                new TeamForm("Beta", 2, 4, 6, 3, "WW", 2, 2, 0),
                new TeamForm("Gama", 3, 3, 3, 3, "LDL", 0, 1, 1),
                new TeamForm("Delta", 4, 4, 0, 3, "LLL", 0, 0, 3));

        assertThat(catalog.contextStories(
                data(List.of(), context(null, forms), List.of()), java.util.Locale.ENGLISH))
                .anyMatch(story -> story.kind() == RecapStoryKind.SURGE);
    }

    @Test
    void contextStories_twoSlidingSides_pickTheWorst() {
        List<TeamForm> forms = List.of(
                new TeamForm("Alpha", 1, 1, 9, 3, "WWW", 3, 3, 0),
                new TeamForm("Beta", 2, 2, 6, 3, "WWW", 3, 3, 0),
                new TeamForm("Gama", 3, 1, 3, 3, "LLL", 0, 0, 3),
                new TeamForm("Delta", 4, 2, 0, 3, "LLL", 0, 0, 4));

        assertThat(catalog.contextStories(
                data(List.of(), context(null, forms), List.of()), java.util.Locale.ENGLISH))
                .anyMatch(story -> story.kind() == RecapStoryKind.COLLAPSE);
    }

    @Test
    void contextStories_twoBreakoutScorers_pickTheSharper() {
        MatchFact match = new MatchFact("m", 3, "Delta", "Alpha", 5, 0, List.of(
                goal("Delta", "Kid"), goal("Delta", "Kid"), goal("Delta", "Kid"),
                goal("Delta", "Ace"), goal("Delta", "Ace")));

        assertThat(catalog.contextStories(
                data(List.of(match), context(null, standardForms()), List.of()), java.util.Locale.ENGLISH))
                .anyMatch(story -> story.kind() == RecapStoryKind.BREAKOUT);
    }

    private TeamForm form(String team, int position, int previousPosition) {
        return new TeamForm(team, position, previousPosition, 0, 3, "", 0, 0, 0);
    }

    private List<RecapStory> rout(String home, String away) {
        return catalog.contextStories(
                data(List.of(new MatchFact("m", 3, home, away, 5, 0, List.of())),
                        context(null, standardForms()), List.of()), java.util.Locale.ENGLISH);
    }

    private GoalFact goal(String team, String scorer) {
        return new GoalFact(true, team, scorer, null, 10, Half.FIRST, false, false);
    }

    private List<TeamForm> standardForms() {
        return List.of(
                new TeamForm("Alpha", 1, 1, 9, 3, "WWW", 3, 3, 0),
                new TeamForm("Beta", 2, 2, 6, 3, "WWL", 0, 0, 1),
                new TeamForm("Gama", 3, 3, 3, 3, "LWL", 0, 0, 1),
                new TeamForm("Delta", 4, 4, 0, 3, "LLL", 0, 0, 3));
    }

    private LeagueContext context(TitleRace titleRace, List<TeamForm> forms) {
        return new LeagueContext(3, 6, forms, titleRace, new SurvivalRace("Delta", 0, "Gama", 3));
    }

    private RoundRecapPromptData data(List<MatchFact> matchFacts, LeagueContext context,
                                      List<RoundRecapPlayerData> topScorers) {
        return new RoundRecapPromptData("Test League", 3, "en", "English",
                List.of(), List.of(), 6, topScorers, List.of(), null,
                matchFacts, context, null);
    }

    private RecapStory find(List<RecapStory> stories, RecapStoryKind kind) {
        return stories.stream().filter(story -> story.kind() == kind).findFirst().orElseThrow();
    }

    private int weight(List<RecapStory> stories, RecapStoryKind kind) {
        return find(stories, kind).weight();
    }
}
