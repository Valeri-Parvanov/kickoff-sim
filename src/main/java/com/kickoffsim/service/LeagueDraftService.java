package com.kickoffsim.service;

import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.TeamDto;

import java.util.List;

public interface LeagueDraftService {

    LeagueWizardForm draft(int format, List<TeamDto> existingTeams);
}
