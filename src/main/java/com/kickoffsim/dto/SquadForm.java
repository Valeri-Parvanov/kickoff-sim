package com.kickoffsim.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SquadForm {

    private List<PlayerRowDto> rows = new ArrayList<>();
}
