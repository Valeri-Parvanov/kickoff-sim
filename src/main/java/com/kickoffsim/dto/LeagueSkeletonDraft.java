package com.kickoffsim.dto;

import java.util.List;

public record LeagueSkeletonDraft(String leagueName, List<TeamNameDraft> teams) {
}
