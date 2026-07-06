package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.LeagueDetailView;
import bg.softuni.footballleague.dto.LeagueDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface LeagueService {

    List<LeagueDto> findAll();

    List<LeagueDto> findAll(Sort sort);

    LeagueDto findById(UUID id);

    LeagueDetailView findDetail(UUID id);

    LeagueDto create(LeagueDto leagueDto);

    LeagueDto update(UUID id, LeagueDto leagueDto);

    void delete(UUID id);

    boolean hasLeagueStarted(UUID leagueId);
}
