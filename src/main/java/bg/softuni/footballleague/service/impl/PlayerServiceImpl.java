package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    @Override
    public List<PlayerDto> findAll() {
        return playerRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<PlayerDto> findAllByTeam(Long teamId) {
        Team team = getTeamOrThrow(teamId);
        return playerRepository.findAllByTeam(team).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public PlayerDto findById(Long id) {
        return toDto(getPlayerOrThrow(id));
    }

    @Override
    public PlayerDto create(PlayerDto playerDto) {
        Player player = new Player();
        mapToEntity(playerDto, player);
        return toDto(playerRepository.save(player));
    }

    @Override
    public PlayerDto update(Long id, PlayerDto playerDto) {
        Player player = getPlayerOrThrow(id);
        mapToEntity(playerDto, player);
        return toDto(playerRepository.save(player));
    }

    @Override
    public void delete(Long id) {
        playerRepository.delete(getPlayerOrThrow(id));
    }

    private Player getPlayerOrThrow(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player with id %d not found".formatted(id)));
    }

    private Team getTeamOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team with id %d not found".formatted(id)));
    }

    private void mapToEntity(PlayerDto playerDto, Player player) {
        player.setFirstName(playerDto.getFirstName());
        player.setLastName(playerDto.getLastName());
        player.setPosition(playerDto.getPosition());
        player.setShirtNumber(playerDto.getShirtNumber());
        player.setTeam(getTeamOrThrow(playerDto.getTeamId()));
    }

    private PlayerDto toDto(Player player) {
        PlayerDto playerDto = new PlayerDto();
        playerDto.setId(player.getId());
        playerDto.setFirstName(player.getFirstName());
        playerDto.setLastName(player.getLastName());
        playerDto.setPosition(player.getPosition());
        playerDto.setShirtNumber(player.getShirtNumber());
        playerDto.setTeamId(player.getTeam() != null ? player.getTeam().getId() : null);
        return playerDto;
    }
}
