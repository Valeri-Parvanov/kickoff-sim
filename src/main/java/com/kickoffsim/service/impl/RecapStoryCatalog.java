package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPlayerData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RecapStoryCatalog {

    private static final int HEADLINE_VARIANTS = 3;

    private static final int COMEBACK_DEFICIT = 2;

    private static final int LATE_MINUTE = 16;

    private static final int LEAD_CHANGES = 2;

    private static final int HAT_TRICK_GOALS = 3;

    private static final int STREAK_LENGTH = 3;

    private static final int THRILLER_GOALS = 5;

    private static final int SQUAD_SIZE = 6;

    private static final int GOAL_WEIGHT = 2;

    private static final int OPEN_TITLE_PENALTY = 50;

    private static final int OPEN_RACE_BOOST = 15;

    private static final int SEASON_SCOPE = 0;

    private static final int ROTATION_SPREAD = 15;

    private static final int CHAMPION_LEAD_WEIGHT = 200;

    private static final int BODY_VARIANTS = 3;

    private static final String SECOND_HALF = "second half";

    private static final String OWN_GOAL = ", own goal";

    private static final String ASSIST = ", assist ";

    private static final String FIELD_SEPARATOR = ", ";

    private static final Pattern GOAL_MINUTE = Pattern.compile("minute (\\d+)");

    private final MessageSource messageSource;

    public List<RecapStory> roundStories(RoundRecapPromptData data, Locale locale) {
        List<RoundRecapMatchData> matches = data.matches();
        List<RecapStory> stories = new ArrayList<>();

        addRoundChampion(stories, data, playedTable(data.standings()), locale);
        addComeback(stories, data, matches, locale);
        addLateDrama(stories, data, matches, locale);
        addSwings(stories, data, matches, locale);
        addStarPlayer(stories, data, matches, locale);
        RoundRecapMatchData headline = addBigWin(stories, data, matches, locale, 0);
        addAwayWin(stories, data, matches, locale, 0, headline);
        addGoalFest(stories, data, matches, locale, 0, headline);
        addSquad(stories, data, matches, locale);
        addResults(stories, matches, locale);
        addStats(stories, matches, locale);
        return stories;
    }

    public List<RecapStory> seasonStories(RoundRecapPromptData data, Locale locale) {
        List<RoundRecapMatchData> matches = data.matches();
        List<RoundRecapStandingData> table = playedTable(data.standings());
        boolean open = isSeasonOpen(data, table);
        int boost = open ? OPEN_RACE_BOOST : 0;
        List<RecapStory> stories = new ArrayList<>();

        addTitle(stories, data, table, locale, open);
        addSecondPlace(stories, data, table, locale, boost);
        addStreak(stories, data, matches, locale, boost);
        addScorerRace(stories, data, locale, boost);
        addAttackDefence(stories, data, table, locale, boost);
        addSeasonRecords(stories, data, matches, locale);
        addBottom(stories, data, table, locale);
        addStats(stories, matches, locale);
        return stories;
    }

    private void addTitle(List<RecapStory> stories, RoundRecapPromptData data,
                          List<RoundRecapStandingData> table, Locale locale, boolean open) {
        if (table.isEmpty()) {
            return;
        }
        RoundRecapStandingData leader = table.get(0);
        if (leader.champion()) {
            int weight = open ? 90 - OPEN_TITLE_PENALTY : 90;
            int coLeaders = (int) table.stream().filter(row -> row.points() == leader.points()).count();
            if (coLeaders > 1) {
                stories.add(storyVaried(RecapStoryKind.TITLE_DECIDED, weight, data, locale, leader.team(),
                        new Object[]{leader.team(), leader.points(), coLeaders, leader.goalDifference()},
                        "title-decided-tiebreak"));
                return;
            }
            stories.add(story(RecapStoryKind.TITLE_DECIDED, weight, data, locale, leader.team(),
                    new Object[]{leader.team(), leader.points(), leader.wins(), leader.draws(), leader.losses()}));
            return;
        }
        if (table.size() < 2) {
            return;
        }
        RoundRecapStandingData second = table.get(1);
        int gap = leader.points() - second.points();
        int remaining = Math.max(0, data.matchesPerTeam() - leader.played());
        if (gap == 0) {
            stories.add(story(RecapStoryKind.TITLE_RACE, 70, data, locale, leader.team(),
                    new Object[]{leader.team(), second.team(), leader.points(), remaining}, "title-race-level"));
            return;
        }
        stories.add(story(RecapStoryKind.TITLE_RACE, Math.max(40, 70 - gap), data, locale, leader.team(),
                new Object[]{leader.team(), leader.points(), second.team(), second.points(), gap, remaining}));
    }

    private void addRoundChampion(List<RecapStory> stories, RoundRecapPromptData data,
                                  List<RoundRecapStandingData> table, Locale locale) {
        Integer clinchRound = data.championClinchRound();
        if (clinchRound == null || data.roundNumber() != clinchRound || table.isEmpty()) {
            return;
        }
        RoundRecapStandingData leader = table.get(0);
        if (!leader.champion()) {
            return;
        }
        int coLeaders = (int) table.stream().filter(row -> row.points() == leader.points()).count();
        if (coLeaders > 1) {
            stories.add(storyVaried(RecapStoryKind.TITLE_DECIDED, CHAMPION_LEAD_WEIGHT, data, locale, leader.team(),
                    new Object[]{leader.team(), leader.points(), coLeaders, leader.goalDifference()},
                    "title-decided-tiebreak"));
            return;
        }
        stories.add(storyVaried(RecapStoryKind.TITLE_DECIDED, CHAMPION_LEAD_WEIGHT, data, locale, leader.team(),
                new Object[]{leader.team(), leader.points()}, "title-clinched"));
    }

    private void addSeasonRecords(List<RecapStory> stories, RoundRecapPromptData data,
                                  List<RoundRecapMatchData> matches, Locale locale) {
        RoundRecapMatchData widest = matches.stream()
                .max(Comparator.comparingInt(this::margin))
                .orElse(null);
        if (widest != null && margin(widest) > 0) {
            boolean homeWon = widest.homeScore() > widest.awayScore();
            String winner = homeWon ? widest.homeTeam() : widest.awayTeam();
            String loser = homeWon ? widest.awayTeam() : widest.homeTeam();
            stories.add(storyVaried(RecapStoryKind.BIG_WIN, 35, data, locale, winner,
                    new Object[]{winner, loser, Math.max(widest.homeScore(), widest.awayScore()),
                            Math.min(widest.homeScore(), widest.awayScore()), margin(widest)},
                    "big-win-season"));
        }
        RoundRecapMatchData richest = matches.stream()
                .filter(match -> match != widest)
                .max(Comparator.comparingInt(match -> match.homeScore() + match.awayScore()))
                .orElse(null);
        if (richest != null && richest.homeScore() + richest.awayScore() > 0) {
            int goals = richest.homeScore() + richest.awayScore();
            stories.add(storyVaried(RecapStoryKind.GOAL_FEST, 30, data, locale, richest.homeTeam(),
                    new Object[]{richest.homeTeam(), richest.awayTeam(), goals,
                            richest.homeScore(), richest.awayScore()},
                    "goal-fest-season"));
        }
    }

    private void addSecondPlace(List<RecapStory> stories, RoundRecapPromptData data,
                                List<RoundRecapStandingData> table, Locale locale, int boost) {
        if (table.size() < 3) {
            return;
        }
        RoundRecapStandingData second = table.get(1);
        RoundRecapStandingData third = table.get(2);
        int gap = second.points() - third.points();
        if (gap == 0) {
            stories.add(story(RecapStoryKind.SECOND_PLACE, 45 + boost, data, locale, second.team(),
                    new Object[]{second.team(), third.team(), second.points()}, "second-place-level"));
            return;
        }
        stories.add(story(RecapStoryKind.SECOND_PLACE, 45 + boost, data, locale, second.team(),
                new Object[]{second.team(), second.points(), third.team(), third.points(), gap}));
    }

    private void addStreak(List<RecapStory> stories, RoundRecapPromptData data,
                           List<RoundRecapMatchData> matches, Locale locale, int boost) {
        Streak best = longestWinStreak(matches);
        if (best == null) {
            return;
        }
        stories.add(story(RecapStoryKind.STREAK, 45 + best.length() * 5 + boost, data, locale, best.team(),
                new Object[]{best.team(), best.length()}));
    }

    private void addScorerRace(List<RecapStory> stories, RoundRecapPromptData data, Locale locale, int boost) {
        List<RoundRecapPlayerData> scorers = data.topScorers() == null ? List.of() : data.topScorers();
        if (scorers.isEmpty()) {
            return;
        }
        RoundRecapPlayerData leader = scorers.get(0);
        List<RoundRecapPlayerData> level = scorers.stream()
                .filter(scorer -> scorer.count() == leader.count())
                .toList();
        if (level.size() > 1) {
            stories.add(story(RecapStoryKind.SCORER_RACE, 40 + boost, data, locale, leader.player(),
                    new Object[]{names(level.stream().map(RoundRecapPlayerData::player).toList(), locale),
                            leader.count()},
                    "scorer-race-level"));
            return;
        }
        RoundRecapPlayerData chaser = scorers.size() > 1 ? scorers.get(1) : null;
        int lead = chaser == null ? leader.count() : leader.count() - chaser.count();
        Object[] args = chaser == null
                ? new Object[]{leader.player(), leader.team(), leader.count()}
                : new Object[]{leader.player(), leader.team(), leader.count(),
                        chaser.player(), chaser.count()};
        stories.add(story(RecapStoryKind.SCORER_RACE, 35 + lead + boost, data, locale, leader.player(),
                args, chaser == null ? "scorer-race-solo" : "scorer-race"));
    }

    private void addAttackDefence(List<RecapStory> stories, RoundRecapPromptData data,
                                  List<RoundRecapStandingData> table, Locale locale, int boost) {
        if (table.isEmpty()) {
            return;
        }
        int mostScored = table.stream().mapToInt(RoundRecapStandingData::goalsFor).max().orElseThrow();
        int fewestConceded = table.stream().mapToInt(RoundRecapStandingData::goalsAgainst).min().orElseThrow();
        List<RoundRecapStandingData> attack = table.stream()
                .filter(row -> row.goalsFor() == mostScored).toList();
        List<RoundRecapStandingData> defence = table.stream()
                .filter(row -> row.goalsAgainst() == fewestConceded).toList();

        if (attack.size() == 1 && defence.size() == 1
                && attack.get(0).team().equals(defence.get(0).team())) {
            stories.add(story(RecapStoryKind.ATTACK_DEFENCE, 40 + boost, data, locale, attack.get(0).team(),
                    new Object[]{attack.get(0).team(), mostScored, fewestConceded}, "attack-defence-double"));
            return;
        }
        stories.add(story(RecapStoryKind.ATTACK_DEFENCE, 30 + boost, data, locale, attack.get(0).team(),
                new Object[]{teamNames(attack, locale), mostScored, teamNames(defence, locale), fewestConceded}));
    }

    private void addBottom(List<RecapStory> stories, RoundRecapPromptData data,
                           List<RoundRecapStandingData> table, Locale locale) {
        if (table.size() < 3) {
            return;
        }
        RoundRecapStandingData last = table.get(table.size() - 1);
        List<RoundRecapStandingData> level = table.stream()
                .filter(row -> row.points() == last.points()).toList();
        stories.add(story(RecapStoryKind.BOTTOM, 20, data, locale, last.team(),
                new Object[]{teamNames(level, locale), last.points(), last.played()}));
    }

    private String teamNames(List<RoundRecapStandingData> rows, Locale locale) {
        return names(rows.stream().map(RoundRecapStandingData::team).toList(), locale);
    }

    private String names(List<String> values, Locale locale) {
        if (values.size() == 1) {
            return values.get(0);
        }
        String last = values.get(values.size() - 1);
        String rest = String.join(", ", values.subList(0, values.size() - 1));
        return msg("recap.join.and", locale, rest, last);
    }

    private void addComeback(List<RecapStory> stories, RoundRecapPromptData data,
                             List<RoundRecapMatchData> matches, Locale locale) {
        matches.stream()
                .map(this::comebackOf)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(c -> c.deficit() * 2 + (c.draw() ? 0 : 1)))
                .ifPresent(c -> stories.add(story(RecapStoryKind.COMEBACK,
                        60 + c.deficit() * 10 + (c.draw() ? 0 : 5), data, locale, c.team(),
                        new Object[]{c.team(), c.deficit(), c.opponent(), c.teamScore(), c.opponentScore()},
                        c.draw() ? "comebackdraw" : "comeback")));
    }

    private void addLateDrama(List<RecapStory> stories, RoundRecapPromptData data,
                              List<RoundRecapMatchData> matches, Locale locale) {
        matches.stream()
                .map(this::lateGoalOf)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(LateGoal::minute))
                .ifPresent(g -> stories.add(story(RecapStoryKind.LATE_DRAMA, 55 + g.minute(), data, locale, g.team(),
                        new Object[]{g.team(), g.opponent(), g.minute(), g.teamScore(), g.opponentScore()},
                        g.draw() ? "lateequaliser" : "latewinner")));
    }

    private void addSwings(List<RecapStory> stories, RoundRecapPromptData data,
                           List<RoundRecapMatchData> matches, Locale locale) {
        matches.stream()
                .map(this::leadSwingOf)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(LeadSwing::changes))
                .ifPresent(swing -> stories.add(story(RecapStoryKind.SWINGS, 50 + swing.changes() * 8,
                        data, locale, swing.match().homeTeam(),
                        new Object[]{swing.match().homeTeam(), swing.match().homeScore(),
                                swing.match().awayScore(), swing.match().awayTeam(),
                                swing.progression(), swing.changes()})));
    }

    private void addStarPlayer(List<RecapStory> stories, RoundRecapPromptData data,
                               List<RoundRecapMatchData> matches, Locale locale) {
        List<Contributor> ranked = tally(matches);
        if (ranked.isEmpty()) {
            return;
        }
        Contributor best = ranked.get(0);
        if (best.goals() >= HAT_TRICK_GOALS) {
            stories.add(story(RecapStoryKind.HAT_TRICK, 40 + best.goals() * 10, data, locale, best.name(),
                    new Object[]{best.name(), best.team(), best.goals(), best.assists()}));
            return;
        }
        stories.add(story(RecapStoryKind.MVP, 40, data, locale, best.name(),
                new Object[]{best.name(), best.team(), best.goals(), best.assists()}));
    }

    private RoundRecapMatchData addBigWin(List<RecapStory> stories, RoundRecapPromptData data,
                                          List<RoundRecapMatchData> matches, Locale locale, int boost) {
        RoundRecapMatchData widest = matches.stream()
                .max(Comparator.comparingInt(this::margin))
                .orElseThrow();
        if (margin(widest) == 0) {
            return null;
        }
        boolean homeWon = widest.homeScore() > widest.awayScore();
        stories.add(story(RecapStoryKind.BIG_WIN, 30 + margin(widest) * 4 + boost, data, locale,
                homeWon ? widest.homeTeam() : widest.awayTeam(),
                new Object[]{homeWon ? widest.homeTeam() : widest.awayTeam(),
                        homeWon ? widest.awayTeam() : widest.homeTeam(),
                        Math.max(widest.homeScore(), widest.awayScore()),
                        Math.min(widest.homeScore(), widest.awayScore()),
                        margin(widest)}));
        return widest;
    }

    private void addAwayWin(List<RecapStory> stories, RoundRecapPromptData data,
                            List<RoundRecapMatchData> matches, Locale locale, int boost,
                            RoundRecapMatchData alreadyTold) {
        matches.stream()
                .filter(m -> m.awayScore() > m.homeScore())
                .filter(m -> m != alreadyTold)
                .max(Comparator.comparingInt(this::margin))
                .ifPresent(away -> stories.add(story(RecapStoryKind.AWAY_WIN, 28 + boost, data, locale,
                        away.awayTeam(),
                        new Object[]{away.awayTeam(), away.homeTeam(), away.awayScore(), away.homeScore()})));
    }

    private void addGoalFest(List<RecapStory> stories, RoundRecapPromptData data,
                             List<RoundRecapMatchData> matches, Locale locale, int boost,
                             RoundRecapMatchData alreadyTold) {
        RoundRecapMatchData richest = matches.stream()
                .filter(m -> m != alreadyTold)
                .max(Comparator.comparingInt(m -> m.homeScore() + m.awayScore()))
                .orElse(null);
        if (richest == null) {
            return;
        }
        int goals = richest.homeScore() + richest.awayScore();
        if (goals == 0) {
            return;
        }
        stories.add(story(RecapStoryKind.GOAL_FEST, 25 + goals * 2 + boost, data, locale, richest.homeTeam(),
                new Object[]{richest.homeTeam(), richest.awayTeam(), goals,
                        richest.homeScore(), richest.awayScore()}));
    }

    private void addSquad(List<RecapStory> stories, RoundRecapPromptData data,
                          List<RoundRecapMatchData> matches, Locale locale) {
        List<Contributor> ranked = tally(matches);
        if (ranked.isEmpty()) {
            return;
        }
        stories.add(listStory(RecapStoryKind.SQUAD, 10, locale,
                ranked.subList(0, Math.min(SQUAD_SIZE, ranked.size()))));
        if (ranked.size() > SQUAD_SIZE) {
            stories.add(listStory(RecapStoryKind.BENCH, 9, locale,
                    ranked.subList(SQUAD_SIZE, Math.min(SQUAD_SIZE * 2, ranked.size()))));
        }
    }

    private RecapStory listStory(RecapStoryKind kind, int weight, Locale locale, List<Contributor> players) {
        List<String> items = players.stream()
                .map(player -> String.join(RecapStory.FIELD_SEPARATOR, player.name(), player.team(),
                        String.valueOf(player.goals()), String.valueOf(player.assists())))
                .toList();
        return new RecapStory(kind, weight, msg("recap.story." + kind.getSlug() + ".head", locale),
                String.join(RecapStory.ITEM_SEPARATOR, items));
    }

    private void addResults(List<RecapStory> stories, List<RoundRecapMatchData> matches, Locale locale) {
        List<String> items = matches.stream()
                .map(match -> link(msg("recap.story.results.item", locale,
                        match.homeTeam(), match.homeScore(), match.awayScore(), match.awayTeam()), match.id()))
                .toList();
        stories.add(new RecapStory(RecapStoryKind.RESULTS, 5,
                msg("recap.story.results.head", locale),
                String.join(RecapStory.ITEM_SEPARATOR, items)));
    }

    private void addStats(List<RecapStory> stories, List<RoundRecapMatchData> matches, Locale locale) {
        int goals = matches.stream().mapToInt(m -> m.homeScore() + m.awayScore()).sum();
        String average = String.format(locale, "%.1f", goals / (double) matches.size());
        long draws = matches.stream().filter(m -> m.homeScore() == m.awayScore()).count();
        long cleanSheets = matches.stream().filter(m -> m.homeScore() == 0 || m.awayScore() == 0).count();
        long goalless = matches.stream().filter(m -> m.homeScore() == 0 && m.awayScore() == 0).count();
        long thrillers = matches.stream().filter(m -> m.homeScore() + m.awayScore() >= THRILLER_GOALS).count();

        List<String> chips = new ArrayList<>();
        chips.add(tile(matches.size(), "recap.stat.matches", locale));
        chips.add(tile(goals, "recap.stat.goals", locale));
        chips.add(tile(average, "recap.stat.average", locale));
        if (draws > 0) {
            chips.add(tile(draws, "recap.stat.draws", locale));
        }
        if (cleanSheets > 0) {
            chips.add(tile(cleanSheets, "recap.stat.cleansheets", locale));
        }
        if (goalless > 0) {
            chips.add(tile(goalless, "recap.stat.goalless", locale));
        }
        if (thrillers > 0) {
            chips.add(tile(thrillers, "recap.stat.thrillers", locale));
        }
        stories.add(new RecapStory(RecapStoryKind.STATS, 1, "",
                String.join(RecapStory.ITEM_SEPARATOR, chips)));
    }

    private String link(String text, String id) {
        return id == null ? text : text + RecapStory.FIELD_SEPARATOR + id;
    }

    private String tile(Object value, String key, Locale locale) {
        return value + RecapStory.FIELD_SEPARATOR + msg(key, locale);
    }

    private RecapStory story(RecapStoryKind kind, int weight, RoundRecapPromptData data,
                             Locale locale, String subject, Object[] args) {
        return story(kind, weight, data, locale, subject, args, kind.getSlug());
    }

    private RecapStory story(RecapStoryKind kind, int weight, RoundRecapPromptData data,
                             Locale locale, String subject, Object[] args, String template) {
        int variant = Math.floorMod((data.leagueName() + data.roundNumber() + subject + template).hashCode(),
                HEADLINE_VARIANTS) + 1;
        String headline = msg("recap.story." + template + ".head." + variant, locale, args);
        String body = msg("recap.story." + template + ".body", locale, args);
        return new RecapStory(kind, rotate(weight, data, kind, template), headline, body);
    }

    private RecapStory storyVaried(RecapStoryKind kind, int weight, RoundRecapPromptData data,
                                   Locale locale, String subject, Object[] args, String template) {
        int seed = (data.leagueName() + data.roundNumber() + subject + template).hashCode();
        int headVariant = Math.floorMod(seed, HEADLINE_VARIANTS) + 1;
        int bodyVariant = Math.floorMod(seed * 31 + 7, BODY_VARIANTS) + 1;
        String headline = msg("recap.story." + template + ".head." + headVariant, locale, args);
        String body = msg("recap.story." + template + ".body." + bodyVariant, locale, args);
        return new RecapStory(kind, rotate(weight, data, kind, template), headline, body);
    }

    private int rotate(int weight, RoundRecapPromptData data, RecapStoryKind kind, String template) {
        if (data.roundNumber() == SEASON_SCOPE) {
            return weight;
        }
        int seed = (data.leagueName() + kind.name() + template).hashCode() + data.roundNumber() * 7;
        return weight + Math.floorMod(seed, ROTATION_SPREAD);
    }

    private List<RoundRecapStandingData> playedTable(List<RoundRecapStandingData> standings) {
        return standings == null ? List.of() : standings.stream()
                .filter(row -> row.played() > 0)
                .sorted(Comparator.comparingInt(RoundRecapStandingData::position))
                .toList();
    }

    private boolean isSeasonOpen(RoundRecapPromptData data, List<RoundRecapStandingData> table) {
        int mostPlayed = table.stream().mapToInt(RoundRecapStandingData::played).max().orElse(0);
        return data.matchesPerTeam() > mostPlayed;
    }

    private Streak longestWinStreak(List<RoundRecapMatchData> matches) {
        Map<String, Integer> runs = new LinkedHashMap<>();
        for (RoundRecapMatchData match : matches) {
            applyRun(runs, match.homeTeam(), match.homeScore() > match.awayScore());
            applyRun(runs, match.awayTeam(), match.awayScore() > match.homeScore());
        }
        return runs.entrySet().stream()
                .filter(entry -> entry.getValue() >= STREAK_LENGTH)
                .max(Map.Entry.comparingByValue())
                .map(entry -> new Streak(entry.getKey(), entry.getValue()))
                .orElse(null);
    }

    private void applyRun(Map<String, Integer> runs, String team, boolean won) {
        runs.merge(team, won ? 1 : 0, (current, added) -> won ? current + 1 : 0);
    }

    private List<Contributor> tally(List<RoundRecapMatchData> matches) {
        Map<Scorer, int[]> counts = new LinkedHashMap<>();
        for (RoundRecapMatchData match : matches) {
            List<String> goals = match.goals();
            if (goals == null) {
                continue;
            }
            for (String goal : goals) {
                String[] parts = goal.split(FIELD_SEPARATOR);
                if (parts.length < 2) {
                    continue;
                }
                if (!goal.contains(OWN_GOAL)) {
                    counts.computeIfAbsent(new Scorer(parts[1], parts[0]), key -> new int[2])[0]++;
                }
                String assistant = assistantOf(goal);
                if (assistant != null) {
                    counts.computeIfAbsent(new Scorer(assistant, parts[0]), key -> new int[2])[1]++;
                }
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new Contributor(entry.getKey().name(), entry.getKey().team(),
                        entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingInt(Contributor::points).reversed()
                        .thenComparing(Comparator.comparingInt(Contributor::goals).reversed())
                        .thenComparing(Contributor::name, Comparator.naturalOrder()))
                .toList();
    }

    private String assistantOf(String goal) {
        int marker = goal.indexOf(ASSIST);
        if (marker < 0) {
            return null;
        }
        int from = marker + ASSIST.length();
        int end = goal.indexOf(FIELD_SEPARATOR, from);
        return end < 0 ? goal.substring(from) : goal.substring(from, end);
    }

    private LeadSwing leadSwingOf(RoundRecapMatchData match) {
        if (lacksUsableTimeline(match)) {
            return null;
        }
        int home = 0;
        int away = 0;
        int leader = 0;
        int changes = 0;
        List<String> steps = new ArrayList<>();

        for (String goal : match.goals()) {
            if (isHomeGoal(match, goal)) {
                home++;
            } else {
                away++;
            }
            steps.add(home + ":" + away);
            int current = Integer.signum(home - away);
            if (current != 0 && current != leader) {
                if (leader != 0) {
                    changes++;
                }
                leader = current;
            }
        }
        return changes < LEAD_CHANGES
                ? null
                : new LeadSwing(match, String.join(FIELD_SEPARATOR, steps), changes);
    }

    private Comeback comebackOf(RoundRecapMatchData match) {
        if (lacksUsableTimeline(match)) {
            return null;
        }
        int home = 0;
        int away = 0;
        int homeDeficit = 0;
        int awayDeficit = 0;
        for (String goal : match.goals()) {
            if (isHomeGoal(match, goal)) {
                home++;
            } else {
                away++;
            }
            homeDeficit = Math.max(homeDeficit, away - home);
            awayDeficit = Math.max(awayDeficit, home - away);
        }
        boolean draw = match.homeScore() == match.awayScore();
        boolean homeRecovered = draw ? homeDeficit >= awayDeficit : match.homeScore() > match.awayScore();
        int deficit = homeRecovered ? homeDeficit : awayDeficit;
        if (deficit < COMEBACK_DEFICIT) {
            return null;
        }
        return homeRecovered
                ? new Comeback(match.homeTeam(), match.awayTeam(), deficit,
                        match.homeScore(), match.awayScore(), draw)
                : new Comeback(match.awayTeam(), match.homeTeam(), deficit,
                        match.awayScore(), match.homeScore(), draw);
    }

    private LateGoal lateGoalOf(RoundRecapMatchData match) {
        if (lacksUsableTimeline(match)) {
            return null;
        }
        String last = match.goals().get(match.goals().size() - 1);
        int minute = minuteOf(last);
        if (minute < LATE_MINUTE || !last.contains(SECOND_HALF)) {
            return null;
        }
        boolean homeScored = isHomeGoal(match, last);
        int homeBefore = homeScored ? match.homeScore() - 1 : match.homeScore();
        int awayBefore = homeScored ? match.awayScore() : match.awayScore() - 1;
        if (Integer.signum(homeBefore - awayBefore) == Integer.signum(match.homeScore() - match.awayScore())) {
            return null;
        }
        boolean draw = match.homeScore() == match.awayScore();
        return homeScored
                ? new LateGoal(match.homeTeam(), match.awayTeam(), minute,
                        match.homeScore(), match.awayScore(), draw)
                : new LateGoal(match.awayTeam(), match.homeTeam(), minute,
                        match.awayScore(), match.homeScore(), draw);
    }

    private boolean lacksUsableTimeline(RoundRecapMatchData match) {
        List<String> goals = match.goals();
        if (goals == null || goals.isEmpty()) {
            return true;
        }
        long home = goals.stream().filter(goal -> isHomeGoal(match, goal)).count();
        return home != match.homeScore() || goals.size() - home != match.awayScore();
    }

    private boolean isHomeGoal(RoundRecapMatchData match, String goal) {
        return goal.startsWith(match.homeTeam() + ",");
    }

    private int minuteOf(String goal) {
        Matcher matcher = GOAL_MINUTE.matcher(goal);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private int margin(RoundRecapMatchData match) {
        return Math.abs(match.homeScore() - match.awayScore());
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    private record Comeback(String team, String opponent, int deficit,
                            int teamScore, int opponentScore, boolean draw) {
    }

    private record LateGoal(String team, String opponent, int minute,
                            int teamScore, int opponentScore, boolean draw) {
    }

    private record LeadSwing(RoundRecapMatchData match, String progression, int changes) {
    }

    private record Streak(String team, int length) {
    }

    private record Scorer(String name, String team) {
    }

    private record Contributor(String name, String team, int goals, int assists) {

        private int points() {
            return goals * GOAL_WEIGHT + assists;
        }
    }
}
