package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.service.RoundRecapAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DataDrivenRoundRecapClient implements RoundRecapAiClient {

    private static final int SEASON_SCOPE = 0;

    private static final int COMEBACK_DEFICIT = 2;

    private static final int LATE_MINUTE = 16;

    private static final String SECOND_HALF = "second half";

    private static final int LEAD_CHANGES = 2;

    private static final String OWN_GOAL = ", own goal";

    private static final String ASSIST = ", assist ";

    private static final String FIELD_SEPARATOR = ", ";

    private static final int SQUAD_SIZE = 6;

    private static final int GOAL_WEIGHT = 2;

    private static final Pattern GOAL_MINUTE = Pattern.compile("minute (\\d+)");

    private final MessageSource messageSource;

    @Override
    public String generate(RoundRecapPromptData data) {
        if (data == null) {
            throw new RoundRecapGenerationException("No data was supplied for the recap.");
        }
        List<RoundRecapMatchData> matches = data.matches() == null ? List.of() : data.matches();
        List<RoundRecapStandingData> standings = data.standings() == null ? List.of() : data.standings();

        if (matches.isEmpty()) {
            throw new RoundRecapGenerationException("There are no played matches to analyse.");
        }

        Locale locale = Locale.forLanguageTag(data.localeTag() == null ? "en" : data.localeTag());
        boolean season = data.roundNumber() == SEASON_SCOPE;

        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(intro(data, matches, locale, season));
        paragraphs.add(highlights(matches, locale, season));

        if (season) {
            String table = table(standings, locale);
            if (table != null) {
                paragraphs.add(table);
            }
        } else {
            String squad = teamOfRound(matches, locale);
            if (squad != null) {
                paragraphs.add(squad);
            }
            paragraphs.add(results(matches, locale));
        }
        return String.join("\n\n", paragraphs);
    }

    private String intro(RoundRecapPromptData data, List<RoundRecapMatchData> matches,
                         Locale locale, boolean season) {
        int goals = matches.stream().mapToInt(m -> m.homeScore() + m.awayScore()).sum();
        String average = String.format(locale, "%.1f", goals / (double) matches.size());
        if (season) {
            return msg("recap.season.intro", locale, data.leagueName(), matches.size(), goals, average);
        }
        return msg("recap.round.intro", locale,
                data.leagueName(), data.roundNumber(), matches.size(), goals, average);
    }

    private String results(List<RoundRecapMatchData> matches, Locale locale) {
        StringBuilder builder = new StringBuilder(msg("recap.results.heading", locale));
        for (RoundRecapMatchData match : matches) {
            builder.append('\n').append(msg("recap.results.line", locale,
                    match.homeTeam(), match.homeScore(), match.awayScore(), match.awayTeam()));
        }
        return builder.toString();
    }

    private String highlights(List<RoundRecapMatchData> matches, Locale locale, boolean season) {
        RoundRecapMatchData widest = matches.stream()
                .max(Comparator.comparingInt(this::margin))
                .orElseThrow();
        RoundRecapMatchData richest = matches.stream()
                .max(Comparator.comparingInt(m -> m.homeScore() + m.awayScore()))
                .orElseThrow();
        long draws = matches.stream().filter(m -> m.homeScore() == m.awayScore()).count();
        long goalless = matches.stream()
                .filter(m -> m.homeScore() == 0 && m.awayScore() == 0).count();
        long cleanSheets = matches.stream()
                .filter(m -> m.homeScore() == 0 || m.awayScore() == 0).count();
        long thrillers = matches.stream()
                .filter(m -> m.homeScore() + m.awayScore() >= 5).count();

        List<String> lines = new ArrayList<>();
        if (margin(widest) > 0) {
            boolean homeWon = widest.homeScore() > widest.awayScore();
            lines.add(msg("recap.highlight.biggestwin", locale,
                    homeWon ? widest.homeTeam() : widest.awayTeam(),
                    homeWon ? widest.awayTeam() : widest.homeTeam(),
                    Math.max(widest.homeScore(), widest.awayScore()),
                    Math.min(widest.homeScore(), widest.awayScore()),
                    margin(widest)));
        }
        matches.stream()
                .filter(m -> m.awayScore() > m.homeScore())
                .max(Comparator.comparingInt(this::margin))
                .ifPresent(away -> lines.add(msg("recap.highlight.biggestaway", locale,
                        away.awayTeam(), away.homeTeam(), away.awayScore(), away.homeScore())));
        if (richest.homeScore() + richest.awayScore() > 0) {
            lines.add(msg("recap.highlight.mostgoals", locale,
                    richest.homeTeam(), richest.awayTeam(),
                    richest.homeScore() + richest.awayScore()));
        }
        if (!season) {
            addLeadChanges(matches, locale, lines);
            addComeback(matches, locale, lines);
            addLateDrama(matches, locale, lines);
            addMvp(matches, locale, lines);
        }
        if (season && thrillers > 0) {
            lines.add(msg("recap.highlight.thrillers", locale, thrillers));
        }
        if (draws > 0) {
            lines.add(msg("recap.highlight.draws", locale, draws));
        }
        if (goalless > 0) {
            lines.add(msg("recap.highlight.goalless", locale, goalless));
        }
        if (cleanSheets > 0) {
            lines.add(msg("recap.highlight.cleansheets", locale, cleanSheets));
        }
        return msg("recap.highlight.heading", locale) + "\n" + String.join("\n", lines);
    }

    private String table(List<RoundRecapStandingData> standings, Locale locale) {
        List<RoundRecapStandingData> played = standings.stream()
                .filter(s -> s.played() > 0)
                .sorted(Comparator.comparingInt(RoundRecapStandingData::position))
                .toList();
        if (played.isEmpty()) {
            return null;
        }

        RoundRecapStandingData leader = played.get(0);
        List<String> lines = new ArrayList<>();
        lines.add(msg(leader.champion() ? "recap.table.champion" : "recap.table.leader", locale,
                leader.team(), leader.points(), leader.wins(), leader.draws(), leader.losses()));

        if (played.size() > 1) {
            RoundRecapStandingData second = played.get(1);
            lines.add(msg("recap.table.gap", locale,
                    second.team(), second.points(), leader.points() - second.points()));
        }

        RoundRecapStandingData bestAttack = played.stream()
                .max(Comparator.comparingInt(RoundRecapStandingData::goalsFor)).orElse(leader);
        RoundRecapStandingData bestDefence = played.stream()
                .min(Comparator.comparingInt(RoundRecapStandingData::goalsAgainst)).orElse(leader);
        lines.add(msg("recap.table.bestattack", locale, bestAttack.team(), bestAttack.goalsFor()));
        lines.add(msg("recap.table.bestdefence", locale, bestDefence.team(), bestDefence.goalsAgainst()));

        if (played.size() > 2) {
            RoundRecapStandingData last = played.get(played.size() - 1);
            lines.add(msg("recap.table.bottom", locale, last.team(), last.points()));
        }

        String heading = msg(leader.champion() ? "recap.table.heading.season" : "recap.table.heading.progress",
                locale);
        return heading + "\n" + String.join("\n", lines);
    }

    private String teamOfRound(List<RoundRecapMatchData> matches, Locale locale) {
        List<Contributor> ranked = tally(matches);
        if (ranked.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder(msg("recap.squad.heading", locale));
        appendSquad(builder, ranked.subList(0, Math.min(SQUAD_SIZE, ranked.size())), locale);

        if (ranked.size() > SQUAD_SIZE) {
            builder.append("\n\n").append(msg("recap.squad.bench", locale));
            appendSquad(builder, ranked.subList(SQUAD_SIZE, Math.min(SQUAD_SIZE * 2, ranked.size())), locale);
        }
        return builder.toString();
    }

    private void appendSquad(StringBuilder builder, List<Contributor> players, Locale locale) {
        for (Contributor player : players) {
            builder.append('\n').append(msg("recap.squad.line", locale,
                    player.name(), player.team(), player.goals(), player.assists()));
        }
    }

    private void addMvp(List<RoundRecapMatchData> matches, Locale locale, List<String> lines) {
        tally(matches).stream()
                .findFirst()
                .ifPresent(best -> lines.add(msg("recap.highlight.mvp", locale,
                        best.name(), best.team(), best.goals(), best.assists())));
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

    private void addLeadChanges(List<RoundRecapMatchData> matches, Locale locale, List<String> lines) {
        matches.stream()
                .map(this::leadSwingOf)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(LeadSwing::changes))
                .ifPresent(swing -> lines.add(msg("recap.highlight.leadchanges", locale,
                        swing.match().homeTeam(), swing.match().homeScore(),
                        swing.match().awayScore(), swing.match().awayTeam(),
                        swing.progression(), swing.changes())));
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

    private void addComeback(List<RoundRecapMatchData> matches, Locale locale, List<String> lines) {
        matches.stream()
                .map(this::comebackOf)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(c -> c.deficit() * 2 + (c.draw() ? 0 : 1)))
                .ifPresent(c -> lines.add(msg(c.draw() ? "recap.highlight.comebackdraw" : "recap.highlight.comeback",
                        locale, c.team(), c.deficit(), c.opponent(), c.teamScore(), c.opponentScore())));
    }

    private void addLateDrama(List<RoundRecapMatchData> matches, Locale locale, List<String> lines) {
        matches.stream()
                .map(this::lateGoalOf)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(LateGoal::minute))
                .ifPresent(g -> lines.add(msg(g.draw() ? "recap.highlight.lateequaliser" : "recap.highlight.latewinner",
                        locale, g.team(), g.opponent(), g.minute(), g.teamScore(), g.opponentScore())));
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

    private record Comeback(String team, String opponent, int deficit,
                            int teamScore, int opponentScore, boolean draw) {
    }

    private record LateGoal(String team, String opponent, int minute,
                            int teamScore, int opponentScore, boolean draw) {
    }

    private record LeadSwing(RoundRecapMatchData match, String progression, int changes) {
    }

    private record Scorer(String name, String team) {
    }

    private record Contributor(String name, String team, int goals, int assists) {

        private int points() {
            return goals * GOAL_WEIGHT + assists;
        }
    }

    private int margin(RoundRecapMatchData match) {
        return Math.abs(match.homeScore() - match.awayScore());
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }
}
