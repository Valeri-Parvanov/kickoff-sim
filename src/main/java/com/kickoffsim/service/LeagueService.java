package com.kickoffsim.service;

import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.LeagueDto;
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

    int deleteFinishedOlderThan(int days);
}
