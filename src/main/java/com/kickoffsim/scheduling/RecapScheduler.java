package com.kickoffsim.scheduling;

import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.RoundRecapRepository;
import com.kickoffsim.service.RoundRecapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecapScheduler {

    private static final int COMPLETION_DELAY_MINUTES = 46;

    private static final int SEASON_SCOPE = 0;

    private static final String MARKER_LOCALE = "en";

    private static final String SCOPE_SEPARATOR = "#";

    private final MatchRepository matchRepository;

    private final RoundRecapRepository roundRecapRepository;

    private final RoundRecapService roundRecapService;

    @Scheduled(cron = "0 */5 * * * *")
    public void generateMissingRecaps() {
        List<Object[]> completedRounds = matchRepository.findCompletedRounds(
                LocalDateTime.now().minusMinutes(COMPLETION_DELAY_MINUTES));
        if (completedRounds.isEmpty()) {
            return;
        }

        Set<String> generated = roundRecapRepository.findScopesByLocale(MARKER_LOCALE).stream()
                .map(scope -> scope[0] + SCOPE_SEPARATOR + scope[1])
                .collect(Collectors.toSet());
        Set<UUID> refreshedLeagues = new LinkedHashSet<>();

        for (Object[] scope : completedRounds) {
            UUID leagueId = (UUID) scope[0];
            Integer roundNumber = (Integer) scope[1];
            if (leagueId == null || roundNumber == null || roundNumber == SEASON_SCOPE
                    || generated.contains(leagueId + SCOPE_SEPARATOR + roundNumber)) {
                continue;
            }
            if (generateRound(leagueId, roundNumber)) {
                refreshedLeagues.add(leagueId);
            }
        }

        refreshedLeagues.forEach(this::generateSeason);
    }

    private boolean generateRound(UUID leagueId, int roundNumber) {
        try {
            roundRecapService.generateAllLanguages(leagueId, roundNumber, false);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Skipped the automatic recap for league {} round {}: {}",
                    leagueId, roundNumber, exception.getMessage());
            return false;
        }
    }

    private void generateSeason(UUID leagueId) {
        try {
            roundRecapService.generateSeasonAllLanguages(leagueId, true);
        } catch (RuntimeException exception) {
            log.warn("Skipped the automatic season recap for league {}: {}", leagueId, exception.getMessage());
        }
    }
}
