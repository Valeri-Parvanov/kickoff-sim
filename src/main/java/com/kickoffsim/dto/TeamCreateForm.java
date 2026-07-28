package com.kickoffsim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TeamCreateForm {

    @NotBlank(message = "{validation.team.name.required}")
    @Size(max = 100, message = "{validation.team.name.max}")
    @Pattern(regexp = "^\\S+$", message = "{validation.team.name.nospaces}")
    private String name;

    @NotBlank(message = "{validation.team.city.required}")
    @Size(max = 100, message = "{validation.team.city.max}")
    @Pattern(regexp = "^\\S+(?: \\S+)*$", message = "{validation.team.city.nospaces}")
    private String city;

    private UUID leagueId;

    private UUID teamId;

    private List<PlayerRowDto> players = new ArrayList<>();
}
