package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchControllerTest {

    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private LeagueService leagueService;

    @InjectMocks
    private SearchController controller;

    @Test
    void suggest_nullQuery_returnsEmptyGroups() {
        Map<String, Object> result = controller.suggest(null);

        assertThat((List<?>) result.get("teams")).isEmpty();
        assertThat((List<?>) result.get("players")).isEmpty();
        assertThat((List<?>) result.get("leagues")).isEmpty();
    }

    @Test
    void suggest_tooShortQuery_returnsEmptyGroups() {
        Map<String, Object> result = controller.suggest("ab");

        assertThat((List<?>) result.get("teams")).isEmpty();
    }

    @Test
    void suggest_validQuery_returnsMappedResultsAcrossAllThree() {
        TeamDto team = new TeamDto();
        team.setId(UUID.randomUUID());
        team.setName("Sofia FC");
        team.setCity("Sofia");
        when(teamService.searchByName("sof")).thenReturn(List.of(team));

        PlayerDto player = new PlayerDto();
        player.setId(UUID.randomUUID());
        player.setFirstName("Ivan");
        player.setLastName("Petrov");
        player.setTeamId(team.getId());
        player.setTeamName("Sofia FC");
        when(playerService.searchByName("sof")).thenReturn(List.of(player));

        LeagueDto league = new LeagueDto();
        league.setId(UUID.randomUUID());
        league.setName("Sofia Cup");
        when(leagueService.searchByName("sof")).thenReturn(List.of(league));

        Map<String, Object> result = controller.suggest(" sof ");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teams = (List<Map<String, Object>>) result.get("teams");
        assertThat(teams).hasSize(1);
        assertThat(teams.get(0)).containsEntry("label", "Sofia FC")
                .containsEntry("sublabel", "Sofia")
                .containsEntry("url", "/teams/" + team.getId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) result.get("players");
        assertThat(players).hasSize(1);
        assertThat(players.get(0)).containsEntry("label", "Ivan Petrov")
                .containsEntry("sublabel", "Sofia FC")
                .containsEntry("url", "/teams/" + team.getId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> leagues = (List<Map<String, Object>>) result.get("leagues");
        assertThat(leagues).hasSize(1);
        assertThat(leagues.get(0)).containsEntry("label", "Sofia Cup")
                .containsEntry("sublabel", "")
                .containsEntry("url", "/leagues/" + league.getId());
    }

    @Test
    void suggest_teamWithoutCity_blankSublabel() {
        TeamDto team = new TeamDto();
        team.setId(UUID.randomUUID());
        team.setName("Free FC");
        team.setCity(null);
        when(teamService.searchByName("free")).thenReturn(List.of(team));
        when(playerService.searchByName("free")).thenReturn(List.of());
        when(leagueService.searchByName("free")).thenReturn(List.of());

        Map<String, Object> result = controller.suggest("free");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teams = (List<Map<String, Object>>) result.get("teams");
        assertThat(teams.get(0)).containsEntry("sublabel", "");
    }

    @Test
    void suggest_playerWithoutTeamName_blankSublabel() {
        PlayerDto player = new PlayerDto();
        player.setId(UUID.randomUUID());
        player.setFirstName("Lone");
        player.setLastName("Wolf");
        player.setTeamId(UUID.randomUUID());
        player.setTeamName(null);
        when(teamService.searchByName("lone")).thenReturn(List.of());
        when(playerService.searchByName("lone")).thenReturn(List.of(player));
        when(leagueService.searchByName("lone")).thenReturn(List.of());

        Map<String, Object> result = controller.suggest("lone");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) result.get("players");
        assertThat(players.get(0)).containsEntry("sublabel", "");
    }
}
