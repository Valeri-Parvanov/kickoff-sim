package com.kickoffsim.dto;

import com.kickoffsim.validation.SquadEntry;
import com.kickoffsim.validation.UniqueShirtNumbers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TeamSquadPayload {

    @NotNull(message = "{validation.squad.team.required}")
    @Valid
    private TeamDto team;

    @NotEmpty(message = "{validation.squad.players.required}")
    @UniqueShirtNumbers
    @Valid
    @ConvertGroup(to = SquadEntry.class)
    private List<PlayerDto> players = new ArrayList<>();
}
