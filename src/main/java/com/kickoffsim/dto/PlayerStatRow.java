package com.kickoffsim.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlayerStatRow {

    private UUID playerId;
    private String playerName;
    private String teamName;
    private String teamCity;
    private int count;
}
