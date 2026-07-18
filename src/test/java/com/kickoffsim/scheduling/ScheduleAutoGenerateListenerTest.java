package com.kickoffsim.scheduling;

import com.kickoffsim.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleAutoGenerateListenerTest {

    @Mock private ScheduleService scheduleService;

    @InjectMocks
    private ScheduleAutoGenerateListener listener;

    @Test
    void onTeamCreated_delegatesToScheduleService() {
        UUID leagueId = UUID.randomUUID();
        TeamCreatedEvent event = new TeamCreatedEvent(leagueId);

        listener.onTeamCreated(event);

        verify(scheduleService).tryAutoGenerate(leagueId);
    }
}
