package com.kickoffsim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LeagueWizardForm {

    @NotBlank(message = "League name is required")
    @Size(max = 100, message = "League name must be at most 100 characters")
    private String leagueName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate scheduleStartDate;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime scheduleStartTime;

    private int format;

    private List<UUID> existingTeamIds = new ArrayList<>();

    private List<TeamCreateForm> newTeams = new ArrayList<>();
}
