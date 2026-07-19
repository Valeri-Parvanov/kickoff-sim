package com.kickoffsim.web;

import com.kickoffsim.dto.StandingRow;

import java.util.List;
import java.util.UUID;

public final class StandingsSupport {

    private StandingsSupport() {
    }

    public static Integer positionOf(List<StandingRow> standings, UUID teamId) {
        for (int i = 0; i < standings.size(); i++) {
            if (teamId.equals(standings.get(i).getTeamId())) {
                return i + 1;
            }
        }
        return null;
    }
}
