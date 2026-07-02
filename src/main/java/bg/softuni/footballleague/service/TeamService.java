package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.TeamDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface TeamService {

    List<TeamDto> findAll();

    List<TeamDto> findAll(Sort sort);

    List<TeamDto> findAllByLeague(UUID leagueId);

    boolean existsByName(String name);

    TeamDto findById(UUID id);

    TeamDto create(TeamDto teamDto);

    TeamDto update(UUID id, TeamDto teamDto);

    void delete(UUID id);
}
