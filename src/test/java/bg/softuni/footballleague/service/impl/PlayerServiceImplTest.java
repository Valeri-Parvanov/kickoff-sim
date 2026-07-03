package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.exception.DuplicateShirtNumberException;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.SquadLimitExceededException;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceImplTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private TeamRepository teamRepository;

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

    private PlayerDto playerDto(int shirtNumber) {
        PlayerDto dto = new PlayerDto();
        dto.setFirstName("Ivan");
        dto.setLastName("Ivanov");
        dto.setShirtNumber(shirtNumber);
        dto.setTeamId(teamId);
        return dto;
    }
}
