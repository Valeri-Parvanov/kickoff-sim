package com.kickoffsim.dto;

import java.util.List;

public record RoundRecapPromptData(
        String leagueName,
        int roundNumber,
        String localeTag,
        String languageName,
        List<RoundRecapMatchData> matches,
        List<RoundRecapStandingData> standings,
        int matchesPerTeam,
        List<RoundRecapPlayerData> topScorers,
        List<RoundRecapPlayerData> topAssists,
        Integer championClinchRound,
        List<MatchFact> matchFacts,
        LeagueContext context,
        RecapMemory memory) {

    public RoundRecapPromptData(
            String leagueName,
            int roundNumber,
            String localeTag,
            String languageName,
            List<RoundRecapMatchData> matches,
            List<RoundRecapStandingData> standings,
            int matchesPerTeam,
            List<RoundRecapPlayerData> topScorers,
            List<RoundRecapPlayerData> topAssists,
            Integer championClinchRound) {
        this(leagueName, roundNumber, localeTag, languageName, matches, standings, matchesPerTeam,
                topScorers, topAssists, championClinchRound, null, null, null);
    }

    public List<MatchFact> matchFacts() {
        return matchFacts == null ? List.of() : matchFacts;
    }

    public RecapMemory memory() {
        return memory == null ? RecapMemory.empty() : memory;
    }
}
