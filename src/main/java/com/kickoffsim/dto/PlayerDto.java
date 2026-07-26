package com.kickoffsim.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlayerDto {

    private UUID id;

    @NotBlank(message = "{validation.player.firstname.required}")
    @Size(max = 100, message = "{validation.player.firstname.max}")
    private String firstName;

    @NotBlank(message = "{validation.player.lastname.required}")
    @Size(max = 100, message = "{validation.player.lastname.max}")
    private String lastName;

    @NotNull(message = "{validation.player.shirt.required}")
    @Positive(message = "{validation.player.shirt.positive}")
    @Max(value = 99, message = "{validation.player.shirt.max}")
    private Integer shirtNumber;

    @NotNull(message = "{validation.player.team.required}")
    private UUID teamId;

    private String teamName;

    private int goals;

    private int assists;
}
