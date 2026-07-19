package com.kickoffsim.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LeagueBundlePayload {

    private String leagueName;

    private LocalDate scheduleStartDate;

    private LocalTime scheduleStartTime;

    private int format;

    private List<UUID> existingTeamIds = new ArrayList<>();

    private List<TeamSquadPayload> newTeams = new ArrayList<>();
}
