package com.kickoffsim.dto;

import com.kickoffsim.validation.SquadEntry;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlayerDto {

    private UUID id;

    @NotBlank(message = "{validation.player.firstname.required}", groups = {Default.class, SquadEntry.class})
    @Size(max = 100, message = "{validation.player.firstname.max}", groups = {Default.class, SquadEntry.class})
    private String firstName;

    @NotBlank(message = "{validation.player.lastname.required}", groups = {Default.class, SquadEntry.class})
    @Size(max = 100, message = "{validation.player.lastname.max}", groups = {Default.class, SquadEntry.class})
    private String lastName;

    @NotNull(message = "{validation.player.shirt.required}", groups = {Default.class, SquadEntry.class})
    @Positive(message = "{validation.player.shirt.positive}", groups = {Default.class, SquadEntry.class})
    @Max(value = 99, message = "{validation.player.shirt.max}", groups = {Default.class, SquadEntry.class})
    private Integer shirtNumber;

    @NotNull(message = "{validation.player.team.required}")
    private UUID teamId;

    private String teamName;

    private int goals;

    private int assists;
}
