package bg.softuni.footballleague.scheduling;

import bg.softuni.footballleague.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchResultSchedulerTest {

    @Mock private ScheduleService scheduleService;

    @InjectMocks
    private MatchResultScheduler scheduler;

    @Test
    void simulatePastMatches_delegatesToScheduleService() {
        scheduler.simulatePastMatches();

        verify(scheduleService).simulatePastMatches();
    }

    @Test
    void notifyMatchEvents_delegatesToScheduleService() {
        scheduler.notifyMatchEvents();

        verify(scheduleService).notifyMatchEvents();
    }

    @Test
    void notifyGoals_delegatesToScheduleService() {
        scheduler.notifyGoals();

        verify(scheduleService).notifyGoals();
    }
}
