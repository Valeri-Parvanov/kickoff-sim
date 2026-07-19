package com.kickoffsim.scheduling;

import com.kickoffsim.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeagueRetentionScheduler {

    private static final int FINISHED_LEAGUE_RETENTION_DAYS = 90;

    private final LeagueService leagueService;

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeFinishedLeagues() {
        leagueService.deleteFinishedOlderThan(FINISHED_LEAGUE_RETENTION_DAYS);
    }
}
