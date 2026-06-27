package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;

    @Override
    public List<TeamDto> findAll() {
        return teamRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<TeamDto> findAllByLeague(Long leagueId) {
        League league = getLeagueOrThrow(leagueId);
        return teamRepository.findAllByLeague(league).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public TeamDto findById(Long id) {
        return toDto(getTeamOrThrow(id));
    }

    @Override
    public TeamDto create(TeamDto teamDto) {
        Team team = new Team();
        mapToEntity(teamDto, team);
        return toDto(teamRepository.save(team));
    }

    @Override
    public TeamDto update(Long id, TeamDto teamDto) {
        Team team = getTeamOrThrow(id);
        mapToEntity(teamDto, team);
        return toDto(teamRepository.save(team));
    }

    @Override
    public void delete(Long id) {
        teamRepository.delete(getTeamOrThrow(id));
    }

    private Team getTeamOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team with id %d not found".formatted(id)));
    }

    private League getLeagueOrThrow(Long id) {
        return leagueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("League with id %d not found".formatted(id)));
    }

    private void mapToEntity(TeamDto teamDto, Team team) {
        team.setName(teamDto.getName());
        team.setCity(teamDto.getCity());
        team.setLeague(getLeagueOrThrow(teamDto.getLeagueId()));
    }

    private TeamDto toDto(Team team) {
        TeamDto teamDto = new TeamDto();
        teamDto.setId(team.getId());
        teamDto.setName(team.getName());
        teamDto.setCity(team.getCity());
        teamDto.setLeagueId(team.getLeague() != null ? team.getLeague().getId() : null);
        return teamDto;
    }
}
