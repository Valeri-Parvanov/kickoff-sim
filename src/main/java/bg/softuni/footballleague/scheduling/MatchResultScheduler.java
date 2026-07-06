package bg.softuni.footballleague.scheduling;

import bg.softuni.footballleague.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchResultScheduler {

    private final ScheduleService scheduleService;

    @Scheduled(fixedRate = 120_000)
    public void simulatePastMatches() {
        scheduleService.simulatePastMatches();
    }
}
