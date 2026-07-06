package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.GoalEventDto;
import bg.softuni.footballleague.dto.MatchDto;
import org.springframework.data.domain.Sort;

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
}
