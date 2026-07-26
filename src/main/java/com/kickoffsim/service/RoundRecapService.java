package com.kickoffsim.service;

import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.RoundRecapView;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface RoundRecapService {

    Optional<RoundRecapView> find(UUID leagueId, int roundNumber, Locale locale);

    RoundRecapView generate(UUID leagueId, int roundNumber, Locale locale, boolean regenerate);

    void generateAllLanguages(UUID leagueId, int roundNumber, boolean regenerate);

    boolean isRoundComplete(LeagueDetailView league, int roundNumber);

    Optional<RoundRecapView> findSeason(UUID leagueId, Locale locale);

    RoundRecapView generateSeason(UUID leagueId, Locale locale, boolean regenerate);

    void generateSeasonAllLanguages(UUID leagueId, boolean regenerate);

    boolean isSeasonRecapReady(LeagueDetailView league);
}
