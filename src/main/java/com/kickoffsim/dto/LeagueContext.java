package com.kickoffsim.dto;

import java.util.List;

public record LeagueContext(
        int roundNumber,
        int totalRounds,
        List<TeamForm> forms,
        TitleRace titleRace,
        SurvivalRace survivalRace) {

    public LeagueContext {
        forms = forms == null ? List.of() : List.copyOf(forms);
    }

    public TeamForm formOf(String team) {
        return forms.stream()
                .filter(form -> form.team().equals(team))
                .findFirst()
                .orElse(null);
    }

    public int positionOf(String team) {
        TeamForm form = formOf(team);
        return form == null ? 0 : form.position();
    }

    public int fieldSize() {
        return forms.size();
    }

    public boolean isTopTier(String team) {
        int position = positionOf(team);
        return position > 0 && position <= Math.max(1, fieldSize() / 3);
    }

    public boolean isBottomTier(String team) {
        int position = positionOf(team);
        return position > 0 && position > fieldSize() - Math.max(1, fieldSize() / 3);
    }

    public record TeamForm(
            String team,
            int position,
            int previousPosition,
            int points,
            int played,
            String recentResults,
            int winStreak,
            int unbeatenStreak,
            int winlessStreak) {

        public int climb() {
            return previousPosition == 0 ? 0 : previousPosition - position;
        }

        public boolean rising() {
            return climb() > 0;
        }

        public boolean falling() {
            return climb() < 0;
        }
    }

    public record TitleRace(
            String leader,
            int leaderPoints,
            String second,
            int secondPoints,
            int gap,
            int remaining,
            boolean decided) {

        public boolean tight() {
            return !decided && gap <= 3;
        }
    }

    public record SurvivalRace(
            String last,
            int lastPoints,
            String safe,
            int cushion) {
    }
}
