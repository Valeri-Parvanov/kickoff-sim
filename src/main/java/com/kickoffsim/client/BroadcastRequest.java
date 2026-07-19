package com.kickoffsim.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastRequest {

    private UUID matchId;
    private UUID homeTeamId;
    private UUID awayTeamId;
    private UUID leagueId;
    private String message;
    private String type;
}
