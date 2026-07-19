package com.kickoffsim.web;

import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.service.TeamService;
import org.springframework.validation.BindingResult;

public final class MatchTeamValidator {

    private MatchTeamValidator() {
    }

    public static void validate(MatchDto matchDto, TeamService teamService, BindingResult bindingResult) {
        if (matchDto.getHomeTeamId() == null || matchDto.getAwayTeamId() == null) {
            return;
        }
        if (matchDto.getHomeTeamId().equals(matchDto.getAwayTeamId())) {
            bindingResult.rejectValue("awayTeamId", "team.same", "Away team must differ from home team");
            return;
        }

        TeamDto homeTeam = teamService.findById(matchDto.getHomeTeamId());
        TeamDto awayTeam = teamService.findById(matchDto.getAwayTeamId());
        if (!homeTeam.getLeagueId().equals(awayTeam.getLeagueId())) {
            bindingResult.rejectValue("awayTeamId", "team.differentLeague", "Both teams must be in the same league");
        }
    }
}
