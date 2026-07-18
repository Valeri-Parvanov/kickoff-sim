package com.kickoffsim.service.impl;

import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.model.League;
import com.kickoffsim.model.Match;
import com.kickoffsim.model.Team;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.repository.TeamRepository;
import com.kickoffsim.scheduling.TeamCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamServiceImplTest {

    @Mock private TeamRepository teamRepository;
    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

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
        when(playerRepository.countByTeam(any())).thenReturn(0L);
    }

    @Test
    void findAll_mapsTeamsWithAndWithoutLeagueToDto() {
        Team withLeague = new Team();
        withLeague.setId(UUID.randomUUID());
        withLeague.setName("Alpha");
        withLeague.setCity("Sofia");
        withLeague.setLeague(league);

        Team withoutLeague = new Team();
        withoutLeague.setId(UUID.randomUUID());
        withoutLeague.setName("Beta");
        withoutLeague.setCity("Plovdiv");

        when(teamRepository.findAll(any(Sort.class))).thenReturn(List.of(withLeague, withoutLeague));

        List<TeamDto> result = teamService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLeagueId()).isEqualTo(leagueId);
        assertThat(result.get(0).getLeagueName()).isEqualTo("First League");
        assertThat(result.get(1).getLeagueId()).isNull();
        assertThat(result.get(1).getLeagueName()).isNull();
    }

    @Test
    void findAll_withExplicitSort_delegatesToRepository() {
        Sort customSort = Sort.by("name");
        when(teamRepository.findAll(customSort)).thenReturn(List.of());

        teamService.findAll(customSort);

        verify(teamRepository).findAll(customSort);
    }

    @Test
    void findAllFree_setsPlayerCountFromRepository() {
        Team free = new Team();
        free.setId(UUID.randomUUID());
        free.setName("Free FC");
        when(teamRepository.findAllByLeagueIsNull()).thenReturn(List.of(free));
        when(playerRepository.countByTeam(free)).thenReturn(4L);

        List<TeamDto> result = teamService.findAllFree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlayerCount()).isEqualTo(4L);
    }

    @Test
    void findAllByLeague_delegatesToRepository() {
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setLeague(league);
        when(teamRepository.findAllByLeague(league)).thenReturn(List.of(team));

        List<TeamDto> result = teamService.findAllByLeague(leagueId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeagueId()).isEqualTo(leagueId);
    }

    @Test
    void findAllByLeague_leagueNotFound_throwsEntityNotFoundException() {
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.findAllByLeague(leagueId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void existsByName_delegatesToRepository() {
        when(teamRepository.existsByName("Alpha")).thenReturn(true);

        assertThat(teamService.existsByName("Alpha")).isTrue();
    }

    @Test
    void existsByNameAndCity_delegatesToRepository() {
        when(teamRepository.existsByNameAndCity("Alpha", "Sofia")).thenReturn(true);

        assertThat(teamService.existsByNameAndCity("Alpha", "Sofia")).isTrue();
    }

    @Test
    void findById_found_returnsMappedDto() {
        UUID id = UUID.randomUUID();
        Team team = new Team();
        team.setId(id);
        team.setName("Alpha");
        team.setCity("Sofia");
        when(teamRepository.findById(id)).thenReturn(Optional.of(team));

        TeamDto result = teamService.findById(id);

        assertThat(result.getName()).isEqualTo("Alpha");
        assertThat(result.getCity()).isEqualTo("Sofia");
    }

    @Test
    void create_withLeague_publishesTeamCreatedEvent() {
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        teamService.create(teamDto());

        ArgumentCaptor<TeamCreatedEvent> captor = ArgumentCaptor.forClass(TeamCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().leagueId()).isEqualTo(leagueId);
    }

    @Test
    void create_withoutLeague_doesNotPublishEvent() {
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamDto dto = teamDto();
        dto.setLeagueId(null);
        teamService.create(dto);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_existingTeam_mapsAndSaves() {
        UUID id = UUID.randomUUID();
        Team existing = new Team();
        existing.setId(id);
        existing.setName("Old Name");
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(teamRepository.save(existing)).thenReturn(existing);

        TeamDto result = teamService.update(id, teamDto());

        assertThat(result.getName()).isEqualTo("Test FC");
        assertThat(result.getLeagueId()).isEqualTo(leagueId);
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
