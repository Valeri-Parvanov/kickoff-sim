package com.kickoffsim.service;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.GoalEventDto;
import com.kickoffsim.dto.MatchDto;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MatchService {

    List<MatchDto> findAll();

    List<MatchDto> findAll(Sort sort);

    MatchDto findById(UUID id);

    MatchDto create(MatchDto matchDto);

    MatchDto update(UUID id, MatchDto matchDto);

    void delete(UUID id);

    GoalDto findGoalById(UUID goalId);

    void addGoal(UUID matchId, GoalEventDto dto);

    void updateGoal(UUID goalId, GoalEventDto dto);

    void deleteGoal(UUID goalId);

    List<MatchDto> findByLeague(UUID leagueId);

    List<LocalDate> findAllMatchDates();

    List<MatchDto> findByDate(LocalDate date);

    List<String> findAllMatchUtcIsos();
}
