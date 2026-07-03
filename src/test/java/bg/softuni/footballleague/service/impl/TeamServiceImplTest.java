package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @Mock private TeamRepository teamRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;

    @InjectMocks
    private TeamServiceImpl teamService;

    private UUID leagueId;
    private League league;

    @BeforeEach
    void setUp() {
        leagueId = UUID.randomUUID();
        league = new League();
        league.setId(leagueId);
        league.setName("First League");
    }

    @Test
    void create_missingLeague_throwsEntityNotFoundException() {
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.create(teamDto()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findById_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_removesRelatedMatchesThenTeam() {
        UUID teamId = UUID.randomUUID();
        Team team = new Team();
        team.setId(teamId);
        team.setName("Test FC");

        List<Match> matches = List.of(new Match(), new Match());

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeamOrAwayTeam(team, team)).thenReturn(matches);

        teamService.delete(teamId);

        verify(matchRepository).deleteAll(matches);
        verify(teamRepository).delete(team);
    }

    @Test
    void delete_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);

        verify(matchRepository, org.mockito.Mockito.never()).deleteAll(anyList());
    }

    private TeamDto teamDto() {
        TeamDto dto = new TeamDto();
        dto.setName("Test FC");
        dto.setCity("Sofia");
        dto.setLeagueId(leagueId);
        return dto;
    }
}
