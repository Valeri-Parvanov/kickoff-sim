package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.PlayerDto;

import java.util.List;
import java.util.UUID;

public interface PlayerService {

    List<PlayerDto> findAll();

    List<PlayerDto> findAllByTeam(UUID teamId);

    PlayerDto findById(UUID id);

    PlayerDto create(PlayerDto playerDto);

    PlayerDto update(UUID id, PlayerDto playerDto);

    void delete(UUID id);
}
