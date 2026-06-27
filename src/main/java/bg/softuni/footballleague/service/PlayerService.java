package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.PlayerDto;

import java.util.List;

public interface PlayerService {

    List<PlayerDto> findAll();

    List<PlayerDto> findAllByTeam(Long teamId);

    PlayerDto findById(Long id);

    PlayerDto create(PlayerDto playerDto);

    PlayerDto update(Long id, PlayerDto playerDto);

    void delete(Long id);
}
