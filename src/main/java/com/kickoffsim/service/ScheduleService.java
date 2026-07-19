package com.kickoffsim.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface ScheduleService {

    void generate(UUID leagueId, LocalDate startDate, LocalTime startTime);

    void tryAutoGenerate(UUID leagueId);

    void simulatePastMatches();

    void notifyMatchEvents();

    void notifyGoals();
}
