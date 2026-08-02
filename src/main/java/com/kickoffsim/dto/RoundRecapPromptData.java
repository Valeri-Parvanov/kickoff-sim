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
        List<RoundRecapPlayerData> topAssists) {
}
