package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private static final int MIN_QUERY_LENGTH = 3;

    private final TeamService teamService;
    private final PlayerService playerService;
    private final LeagueService leagueService;

    @GetMapping("/search/suggest")
    @ResponseBody
    public Map<String, Object> suggest(@RequestParam(required = false) String q) {
        if (q == null || q.trim().length() < MIN_QUERY_LENGTH) {
            return Map.of("teams", List.of(), "players", List.of(), "leagues", List.of());
        }
        String needle = q.trim();

        List<Map<String, Object>> teams = teamService.searchByName(needle).stream()
                .map(this::toResult)
                .toList();
        List<Map<String, Object>> players = playerService.searchByName(needle).stream()
                .map(this::toResult)
                .toList();
        List<Map<String, Object>> leagues = leagueService.searchByName(needle).stream()
                .map(this::toResult)
                .toList();

        return Map.of("teams", teams, "players", players, "leagues", leagues);
    }

    private Map<String, Object> toResult(TeamDto team) {
        return Map.of(
                "id", team.getId().toString(),
                "label", team.getName(),
                "sublabel", team.getCity() != null ? team.getCity() : "",
                "url", "/teams/" + team.getId());
    }

    private Map<String, Object> toResult(PlayerDto player) {
        return Map.of(
                "id", player.getId().toString(),
                "label", player.getFirstName() + " " + player.getLastName(),
                "sublabel", player.getTeamName() != null ? player.getTeamName() : "",
                "url", "/teams/" + player.getTeamId());
    }

    private Map<String, Object> toResult(LeagueDto league) {
        return Map.of(
                "id", league.getId().toString(),
                "label", league.getName(),
                "sublabel", "",
                "url", "/leagues/" + league.getId());
    }
}
