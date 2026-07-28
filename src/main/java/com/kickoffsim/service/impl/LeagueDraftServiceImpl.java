package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.service.LeagueDraftFactory;
import com.kickoffsim.service.LeagueDraftService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.web.LeagueDraftSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeagueDraftServiceImpl implements LeagueDraftService {

    private final LeagueDraftFactory draftFactory;
    private final TeamService teamService;

    @Override
    public LeagueWizardForm draft(int format, List<TeamDto> existingTeams) {
        List<TeamDto> selected = existingTeams == null ? List.of() : existingTeams;
        int newTeamCount = Math.max(format - selected.size(), 0);

        LeagueSkeletonDraft skeleton = draftFactory.createSkeleton(newTeamCount);

        List<String> reservedNames = selected.stream().map(TeamDto::getName).toList();
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(skeleton, newTeamCount,
                reservedNames, teamService::existsByNameAndCity);

        for (int i = 0; i < teams.size(); i++) {
            int size = LeagueDraftSanitizer.squadSizeFor(skeleton, i);
            LeagueDraftSanitizer.fillSquad(teams.get(i), draftFactory.createSquad(size), size, i);
        }

        LeagueWizardForm form = new LeagueWizardForm();
        form.setFormat(format);
        form.setLeagueName(LeagueDraftSanitizer.sanitizeLeagueName(
                skeleton == null ? null : skeleton.leagueName()));
        form.setExistingTeamIds(new ArrayList<>(selected.stream().map(TeamDto::getId).toList()));
        form.getNewTeams().addAll(teams);
        return form;
    }
}
