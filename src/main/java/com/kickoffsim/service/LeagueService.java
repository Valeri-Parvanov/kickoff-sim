package com.kickoffsim.service;

import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.StandingRow;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface LeagueService {

    List<LeagueDto> findAll();

    List<LeagueDto> findAllOptions();

    List<LeagueDto> findAll(Sort sort);

    LeagueDto findById(UUID id);

    List<LeagueDto> searchByName(String q);

    LeagueDetailView findDetail(UUID id);

    List<StandingRow> findStandings(UUID leagueId);

    LeagueDto create(LeagueDto leagueDto);

    LeagueDto update(UUID id, LeagueDto leagueDto);

    void delete(UUID id);

    boolean hasLeagueStarted(UUID leagueId);

    int deleteFinishedOlderThan(int days);
}
