package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueServiceImplTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;

    @InjectMocks
    private LeagueServiceImpl leagueService;

    @Test
    void create_savesAndReturnsDto() {
        UUID teamId = UUID.randomUUID();
        Team team = new Team();
        team.setId(teamId);
        team.setLeague(null);

        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.findAllById(any())).thenReturn(List.of(team));
        when(playerRepository.countByTeam(team)).thenReturn(6L);
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeagueDto dto = new LeagueDto();
        dto.setName("First League");
        dto.setTeamIds(List.of(teamId));

        LeagueDto saved = leagueService.create(dto);

        assertThat(saved.getName()).isEqualTo("First League");
        verify(leagueRepository).save(any(League.class));
    }

    @Test
    void findById_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_removesMatchesForEachTeamThenLeague() {
        UUID leagueId = UUID.randomUUID();

        Team teamA = new Team();
        teamA.setId(UUID.randomUUID());
        Team teamB = new Team();
        teamB.setId(UUID.randomUUID());

        League league = new League();
        league.setId(leagueId);
        league.getTeams().addAll(List.of(teamA, teamB));

        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(matchRepository.findAllByHomeTeamOrAwayTeam(teamA, teamA)).thenReturn(List.of(new Match()));
        when(matchRepository.findAllByHomeTeamOrAwayTeam(teamB, teamB)).thenReturn(List.of(new Match()));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        leagueService.delete(leagueId);

        verify(matchRepository).findAllByHomeTeamOrAwayTeam(teamA, teamA);
        verify(matchRepository).findAllByHomeTeamOrAwayTeam(teamB, teamB);
        verify(leagueRepository).delete(league);
    }

    @Test
    void delete_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
