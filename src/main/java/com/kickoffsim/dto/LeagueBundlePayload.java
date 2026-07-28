package com.kickoffsim.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "{validation.league.name.required}")
    @Size(max = 100, message = "{validation.league.name.max}")
    private String leagueName;

    private LocalDate scheduleStartDate;

    private LocalTime scheduleStartTime;

    private int format;

    private List<UUID> existingTeamIds = new ArrayList<>();

    @Valid
    private List<TeamSquadPayload> newTeams = new ArrayList<>();
}
