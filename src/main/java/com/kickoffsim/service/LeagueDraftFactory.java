package com.kickoffsim.service;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.SquadDraft;

public interface LeagueDraftFactory {

    LeagueSkeletonDraft createSkeleton(int teamCount);

    SquadDraft createSquad(int squadSize);
}
