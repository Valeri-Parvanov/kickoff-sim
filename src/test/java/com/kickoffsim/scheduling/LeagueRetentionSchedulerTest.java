package com.kickoffsim.scheduling;

import com.kickoffsim.service.LeagueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeagueRetentionSchedulerTest {

    @Mock private LeagueService leagueService;

    @InjectMocks
    private LeagueRetentionScheduler scheduler;

    @Test
    void purgeFinishedLeagues_delegatesToService() {
        scheduler.purgeFinishedLeagues();

        verify(leagueService).deleteFinishedOlderThan(anyInt());
    }
}
