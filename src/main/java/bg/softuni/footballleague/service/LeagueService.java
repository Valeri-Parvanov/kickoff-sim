package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.LeagueDto;

import java.util.List;

public interface LeagueService {

    List<LeagueDto> findAll();

    LeagueDto findById(Long id);

    LeagueDto create(LeagueDto leagueDto);

    LeagueDto update(Long id, LeagueDto leagueDto);

    void delete(Long id);
}
