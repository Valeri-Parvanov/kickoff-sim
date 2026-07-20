package com.kickoffsim.service.impl;

import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.exception.DuplicateShirtNumberException;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.SquadLimitExceededException;
import com.kickoffsim.model.Player;
import com.kickoffsim.model.Team;
import com.kickoffsim.repository.GoalRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerServiceImplTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private GoalRepository goalRepository;

    @InjectMocks
    private PlayerServiceImpl playerService;

    private UUID teamId;
    private Team team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        team = new Team();
        team.setId(teamId);
        team.setName("Test FC");
        when(goalRepository.countGoalsByTeamGroupedByScorer(any(), any())).thenReturn(List.of());
        when(goalRepository.countAssistsByTeamGroupedByAssistant(any(), any())).thenReturn(List.of());
    }

    @Test
    void findAll_mapsPlayersWithAndWithoutTeamToDto() {
        Player withTeam = new Player();
        withTeam.setId(UUID.randomUUID());
        withTeam.setFirstName("Ivan");
        withTeam.setLastName("Ivanov");
        withTeam.setShirtNumber(7);
        withTeam.setTeam(team);

        Player withoutTeam = new Player();
        withoutTeam.setId(UUID.randomUUID());
        withoutTeam.setFirstName("Petar");
        withoutTeam.setLastName("Petrov");
        withoutTeam.setShirtNumber(9);

        when(playerRepository.findAll(any(Sort.class))).thenReturn(List.of(withTeam, withoutTeam));

        List<PlayerDto> result = playerService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTeamId()).isEqualTo(teamId);
        assertThat(result.get(0).getTeamName()).isEqualTo("Test FC");
        assertThat(result.get(1).getTeamId()).isNull();
        assertThat(result.get(1).getTeamName()).isNull();
    }

    @Test
    void findAll_withExplicitSort_delegatesToRepository() {
        Sort customSort = Sort.by("firstName");
        when(playerRepository.findAll(customSort)).thenReturn(List.of());

        playerService.findAll(customSort);

        verify(playerRepository).findAll(customSort);
    }

    @Test
    void findAllByTeam_sortsByShirtNumberAndMaps() {
        Player p9 = new Player();
        p9.setId(UUID.randomUUID());
        p9.setFirstName("A");
        p9.setLastName("B");
        p9.setShirtNumber(9);
        p9.setTeam(team);

        Player p1 = new Player();
        p1.setId(UUID.randomUUID());
        p1.setFirstName("C");
        p1.setLastName("D");
        p1.setShirtNumber(1);
        p1.setTeam(team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.findAllByTeam(team)).thenReturn(List.of(p9, p1));

        List<PlayerDto> result = playerService.findAllByTeam(teamId);

        assertThat(result).extracting(PlayerDto::getShirtNumber).containsExactly(1, 9);
    }

    @Test
    void findAllByTeam_populatesGoalsAndAssistsFromRepository() {
        Player p9 = new Player();
        p9.setId(UUID.randomUUID());
        p9.setFirstName("A");
        p9.setLastName("B");
        p9.setShirtNumber(9);
        p9.setTeam(team);

        Player p1 = new Player();
        p1.setId(UUID.randomUUID());
        p1.setFirstName("C");
        p1.setLastName("D");
        p1.setShirtNumber(1);
        p1.setTeam(team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.findAllByTeam(team)).thenReturn(List.of(p9, p1));
        when(goalRepository.countGoalsByTeamGroupedByScorer(eq(teamId), any()))
                .thenReturn(List.<Object[]>of(new Object[]{p9.getId(), 5L}));
        when(goalRepository.countAssistsByTeamGroupedByAssistant(eq(teamId), any()))
                .thenReturn(List.<Object[]>of(new Object[]{p1.getId(), 3L}));

        List<PlayerDto> result = playerService.findAllByTeam(teamId);

        PlayerDto dtoP1 = result.stream().filter(d -> d.getId().equals(p1.getId())).findFirst().orElseThrow();
        PlayerDto dtoP9 = result.stream().filter(d -> d.getId().equals(p9.getId())).findFirst().orElseThrow();
        assertThat(dtoP9.getGoals()).isEqualTo(5);
        assertThat(dtoP9.getAssists()).isZero();
        assertThat(dtoP1.getAssists()).isEqualTo(3);
        assertThat(dtoP1.getGoals()).isZero();
    }

    @Test
    void findAllByTeam_teamNotFound_throwsEntityNotFoundException() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.findAllByTeam(teamId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void squadRemainingSlots_computesRemainingCapacity() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.countByTeam(team)).thenReturn(9L);

        assertThat(playerService.squadRemainingSlots(teamId)).isEqualTo(3);
    }

    @Test
    void squadRemainingSlots_overCapacity_clampsToZero() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.countByTeam(team)).thenReturn(15L);

        assertThat(playerService.squadRemainingSlots(teamId)).isZero();
    }

    @Test
    void findById_found_returnsMappedDto() {
        UUID id = UUID.randomUUID();
        Player player = new Player();
        player.setId(id);
        player.setFirstName("Ivan");
        player.setLastName("Ivanov");
        player.setShirtNumber(7);
        player.setTeam(team);
        when(playerRepository.findById(id)).thenReturn(Optional.of(player));

        PlayerDto result = playerService.findById(id);

        assertThat(result.getFirstName()).isEqualTo("Ivan");
        assertThat(result.getTeamId()).isEqualTo(teamId);
    }

    @Test
    void update_switchingToDifferentTeam_targetTeamHasSpace_succeeds() {
        UUID playerId = UUID.randomUUID();
        UUID newTeamId = UUID.randomUUID();
        Team newTeam = new Team();
        newTeam.setId(newTeamId);
        newTeam.setName("New FC");

        Player existing = new Player();
        existing.setId(playerId);
        existing.setShirtNumber(7);
        existing.setTeam(team);
        existing.setFirstName("Ivan");
        existing.setLastName("Ivanov");

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(newTeamId)).thenReturn(Optional.of(newTeam));
        when(playerRepository.countByTeam(newTeam)).thenReturn(5L);
        when(playerRepository.findByTeamAndShirtNumber(newTeam, 7)).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerDto dto = playerDto(7);
        dto.setId(playerId);
        dto.setTeamId(newTeamId);
        playerService.update(playerId, dto);

        verify(playerRepository).save(any(Player.class));
    }

    @Test
    void update_switchingToDifferentTeam_targetTeamFull_throwsSquadLimitExceededException() {
        UUID playerId = UUID.randomUUID();
        UUID newTeamId = UUID.randomUUID();
        Team newTeam = new Team();
        newTeam.setId(newTeamId);
        newTeam.setName("New FC");

        Player existing = new Player();
        existing.setId(playerId);
        existing.setShirtNumber(7);
        existing.setTeam(team);

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(newTeamId)).thenReturn(Optional.of(newTeam));
        when(playerRepository.countByTeam(newTeam)).thenReturn(12L);

        PlayerDto dto = playerDto(7);
        dto.setId(playerId);
        dto.setTeamId(newTeamId);

        assertThatThrownBy(() -> playerService.update(playerId, dto))
                .isInstanceOf(SquadLimitExceededException.class);
    }

    @Test
    void create_squadFull_throwsSquadLimitExceededException() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.countByTeam(team)).thenReturn(12L);

        PlayerDto dto = playerDto(7);

        assertThatThrownBy(() -> playerService.create(dto))
                .isInstanceOf(SquadLimitExceededException.class)
                .hasMessageContaining("maximum of 12");
    }

    @Test
    void create_squadNotFull_saves() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.countByTeam(team)).thenReturn(11L);
        when(playerRepository.findByTeamAndShirtNumber(team, 7)).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        playerService.create(playerDto(7));

        verify(playerRepository).save(any(Player.class));
    }

    @Test
    void create_duplicateShirtNumber_throwsDuplicateShirtNumberException() {
        Player existing = new Player();
        existing.setId(UUID.randomUUID());
        existing.setShirtNumber(10);
        existing.setTeam(team);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.countByTeam(team)).thenReturn(5L);
        when(playerRepository.findByTeamAndShirtNumber(team, 10)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> playerService.create(playerDto(10)))
                .isInstanceOf(DuplicateShirtNumberException.class)
                .hasMessageContaining("Shirt number 10");
    }

    @Test
    void update_keepingSameShirtNumber_doesNotThrow() {
        UUID playerId = UUID.randomUUID();

        Player existing = new Player();
        existing.setId(playerId);
        existing.setShirtNumber(10);
        existing.setTeam(team);
        existing.setFirstName("Ivan");
        existing.setLastName("Ivanov");

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(playerRepository.findByTeamAndShirtNumber(team, 10)).thenReturn(Optional.of(existing));
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerDto dto = playerDto(10);
        dto.setId(playerId);
        playerService.update(playerId, dto);

        verify(playerRepository).save(any(Player.class));
    }

    @Test
    void findById_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(playerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(playerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_found_removesPlayer() {
        UUID id = UUID.randomUUID();
        Player player = new Player();
        player.setId(id);
        when(playerRepository.findById(id)).thenReturn(Optional.of(player));

        playerService.delete(id);

        verify(playerRepository).delete(player);
    }

    private PlayerDto playerDto(int shirtNumber) {
        PlayerDto dto = new PlayerDto();
        dto.setFirstName("Ivan");
        dto.setLastName("Ivanov");
        dto.setShirtNumber(shirtNumber);
        dto.setTeamId(teamId);
        return dto;
    }
}
