package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.LeagueDto;

import java.util.List;
import java.util.UUID;

public interface LeagueService {

    List<LeagueDto> findAll();

    LeagueDto findById(UUID id);

    LeagueDto create(LeagueDto leagueDto);

    LeagueDto update(UUID id, LeagueDto leagueDto);

    void delete(UUID id);
}
