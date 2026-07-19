package com.kickoffsim.dto;

import com.kickoffsim.model.LeagueFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LeagueDetailView {

    private UUID id;
    private String name;
    private List<TeamDto> teams;
    private List<StandingRow> standings;
    private List<PlayerStatRow> topScorers;
    private List<PlayerStatRow> topAssists;
    private List<MatchDto> matches;
    private LeagueFormat format;
    private LocalDate scheduleStartDate;
    private LocalTime scheduleStartTime;
}
