package com.kickoffsim.scheduling;

import com.kickoffsim.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduleAutoGenerateListener {

    private final ScheduleService scheduleService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTeamCreated(TeamCreatedEvent event) {
        scheduleService.tryAutoGenerate(event.leagueId());
    }
}
