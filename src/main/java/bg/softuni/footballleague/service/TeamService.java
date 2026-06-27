package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.TeamDto;

import java.util.List;

public interface TeamService {

    List<TeamDto> findAll();

    List<TeamDto> findAllByLeague(Long leagueId);

    TeamDto findById(Long id);

    TeamDto create(TeamDto teamDto);

    TeamDto update(Long id, TeamDto teamDto);

    void delete(Long id);
}
