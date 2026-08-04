package com.kickoffsim.service.impl;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.GoalFact;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.MatchFact;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class FactCollector {

    public List<MatchFact> collect(List<MatchDto> matches, boolean includeGoals) {
        if (matches == null) {
            return List.of();
        }
        return matches.stream()
                .sorted(Comparator.comparing(MatchDto::getPlayedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(match -> String.valueOf(match.getId())))
                .map(match -> toMatchFact(match, includeGoals))
                .toList();
    }

    public MatchFact toMatchFact(MatchDto match, boolean includeGoals) {
        String homeTeam = teamLabel(match.getHomeTeamName(), match.getHomeTeamCity());
        String awayTeam = teamLabel(match.getAwayTeamName(), match.getAwayTeamCity());
        List<GoalFact> goals = includeGoals
                ? match.getGoalTimeline().stream()
                        .map(goal -> toGoalFact(goal, homeTeam, awayTeam))
                        .toList()
                : List.of();
        return new MatchFact(idOf(match), match.getRoundNumber(), homeTeam, awayTeam,
                score(match.getHomeScore()), score(match.getAwayScore()), goals);
    }

    private GoalFact toGoalFact(GoalDto goal, String homeTeam, String awayTeam) {
        return new GoalFact(goal.isHomeGoal(), goal.isHomeGoal() ? homeTeam : awayTeam,
                goal.getScorerName(), goal.getAssistantName(), goal.getMinute(),
                goal.getHalf(), goal.isPenalty(), goal.isOwnGoal());
    }

    private String teamLabel(String name, String city) {
        return city == null || city.isBlank() ? name : name + " (" + city + ")";
    }

    private String idOf(MatchDto match) {
        return match.getId() == null ? null : match.getId().toString();
    }

    private int score(Integer value) {
        return value == null ? 0 : value;
    }
}
