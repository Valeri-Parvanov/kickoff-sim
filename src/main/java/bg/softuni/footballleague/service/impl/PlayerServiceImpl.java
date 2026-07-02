package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.exception.DuplicateShirtNumberException;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.SquadLimitExceededException;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerServiceImpl implements PlayerService {

    private static final int MAX_SQUAD_SIZE = 12;
    private static final Sort DEFAULT_SORT = Sort.by("team.name").and(Sort.by("shirtNumber"));

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    @Override
    public List<PlayerDto> findAll() {
        return findAll(DEFAULT_SORT);
    }

    @Override
    public List<PlayerDto> findAll(Sort sort) {
        return playerRepository.findAll(sort).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<PlayerDto> findAllByTeam(UUID teamId) {
        Team team = getTeamOrThrow(teamId);
        return playerRepository.findAllByTeam(team).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public int squadRemainingSlots(UUID teamId) {
        Team team = getTeamOrThrow(teamId);
        long taken = playerRepository.countByTeam(team);
        return Math.max(0, MAX_SQUAD_SIZE - (int) taken);
    }

    @Override
    public PlayerDto findById(UUID id) {
        return toDto(getPlayerOrThrow(id));
    }

    @Override
    @Transactional
    public PlayerDto create(PlayerDto playerDto) {
        Player player = new Player();
        mapToEntity(playerDto, player);
        PlayerDto saved = toDto(playerRepository.save(player));
        log.info("Created player '{} {}' (#{})", saved.getFirstName(), saved.getLastName(), saved.getShirtNumber());
        return saved;
    }

    @Override
    @Transactional
    public PlayerDto update(UUID id, PlayerDto playerDto) {
        Player player = getPlayerOrThrow(id);
        mapToEntity(playerDto, player);
        PlayerDto saved = toDto(playerRepository.save(player));
        log.info("Updated player {}", id);
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        playerRepository.delete(getPlayerOrThrow(id));
        log.info("Deleted player {}", id);
    }

    private Player getPlayerOrThrow(UUID id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player with id %s not found".formatted(id)));
    }

    private Team getTeamOrThrow(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team with id %s not found".formatted(id)));
    }

    private void mapToEntity(PlayerDto playerDto, Player player) {
        Team team = getTeamOrThrow(playerDto.getTeamId());
        boolean joiningTeam = player.getId() == null || !team.getId().equals(player.getTeam().getId());

        if (joiningTeam && playerRepository.countByTeam(team) >= MAX_SQUAD_SIZE) {
            throw new SquadLimitExceededException(
                    "Team '%s' already has the maximum of %d players".formatted(team.getName(), MAX_SQUAD_SIZE));
        }

        playerRepository.findByTeamAndShirtNumber(team, playerDto.getShirtNumber())
                .filter(existing -> !existing.getId().equals(player.getId()))
                .ifPresent(existing -> {
                    throw new DuplicateShirtNumberException(
                            "Shirt number %d is already taken in team '%s'"
                                    .formatted(playerDto.getShirtNumber(), team.getName()));
                });

        player.setFirstName(playerDto.getFirstName());
        player.setLastName(playerDto.getLastName());
        player.setShirtNumber(playerDto.getShirtNumber());
        player.setTeam(team);
    }

    private PlayerDto toDto(Player player) {
        PlayerDto playerDto = new PlayerDto();
        playerDto.setId(player.getId());
        playerDto.setFirstName(player.getFirstName());
        playerDto.setLastName(player.getLastName());
        playerDto.setShirtNumber(player.getShirtNumber());
        if (player.getTeam() != null) {
            playerDto.setTeamId(player.getTeam().getId());
            playerDto.setTeamName(player.getTeam().getName());
        }
        return playerDto;
    }
}
