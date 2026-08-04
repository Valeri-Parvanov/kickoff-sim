package com.kickoffsim.service.impl;

import com.kickoffsim.dto.GoalFact;
import com.kickoffsim.dto.MatchFact;
import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPlayerData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.dto.RoundRecapStandingData;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RecapValidator {

    private static final String UNRESOLVED_KEY = "recap.";

    public List<RecapStory> validate(List<RecapStory> stories, RoundRecapPromptData data) {
        Set<String> known = knownNames(data);
        return stories.stream()
                .filter(story -> isSound(story, known))
                .toList();
    }

    private boolean isSound(RecapStory story, Set<String> known) {
        if (story.headline() == null || story.body() == null) {
            return false;
        }
        if (story.headline().contains(UNRESOLVED_KEY) || story.body().contains(UNRESOLVED_KEY)) {
            return false;
        }
        if (story.kind().getFamily() != RecapStoryFamily.NARRATIVE) {
            return true;
        }
        return !story.headline().isBlank() && mentionsKnown(story, known);
    }

    private boolean mentionsKnown(RecapStory story, Set<String> known) {
        String text = story.headline() + " " + story.body();
        return known.stream().anyMatch(text::contains);
    }

    private Set<String> knownNames(RoundRecapPromptData data) {
        Set<String> names = new LinkedHashSet<>();
        for (MatchFact match : data.matchFacts()) {
            names.add(match.homeTeam());
            names.add(match.awayTeam());
            for (GoalFact goal : match.goals()) {
                if (goal.scorer() != null) {
                    names.add(goal.scorer());
                }
                if (goal.hasAssist()) {
                    names.add(goal.assistant());
                }
            }
        }
        if (data.matches() != null) {
            for (RoundRecapMatchData match : data.matches()) {
                names.add(match.homeTeam());
                names.add(match.awayTeam());
            }
        }
        addPlayers(names, data.topScorers());
        addPlayers(names, data.topAssists());
        addStandings(names, data.standings());
        return names;
    }

    private void addPlayers(Set<String> names, List<RoundRecapPlayerData> players) {
        if (players == null) {
            return;
        }
        for (RoundRecapPlayerData player : players) {
            names.add(player.player());
            names.add(player.team());
        }
    }

    private void addStandings(Set<String> names, List<RoundRecapStandingData> standings) {
        if (standings == null) {
            return;
        }
        for (RoundRecapStandingData row : standings) {
            names.add(row.team());
        }
    }
}
