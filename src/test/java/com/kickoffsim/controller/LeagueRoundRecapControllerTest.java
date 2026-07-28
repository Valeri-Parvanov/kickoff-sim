package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.RoundRecapView;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.service.*;
import com.kickoffsim.web.MatchFollowSupport;
import com.kickoffsim.security.NotFoundAccessDeniedHandler;
import com.kickoffsim.security.SecurityConfig;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeagueController.class)
@Import(SecurityConfig.class)
class LeagueRoundRecapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueService leagueService;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private ChangeRequestService changeRequestService;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private MatchFollowSupport matchFollowSupport;

    @MockitoBean
    private NotificationClient notificationClient;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RoundRecapService roundRecapService;

    @MockitoBean
    private NotFoundAccessDeniedHandler notFoundAccessDeniedHandler;

    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void configureAccessDeniedHandler() throws Exception {
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }).when(notFoundAccessDeniedHandler).handle(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGenerateRecap() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/leagues/{id}/rounds/{round}/recap", id, 2)
                        .param("regenerate", "false")
                        .locale(Locale.ENGLISH)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/leagues/" + id + "?round=2&tab=overview#overview"))
                .andExpect(flash().attribute("statusMessage", "flash.recap.generated"));

        verify(roundRecapService).generateAllLanguages(id, 2, false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRegenerateRecap() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/leagues/{id}/rounds/{round}/recap", id, 3)
                        .param("regenerate", "true")
                        .locale(Locale.GERMAN)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("statusMessage", "flash.recap.regenerated"));

        verify(roundRecapService).generateAllLanguages(id, 3, true);
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotGenerateRecap() throws Exception {
        UUID id = UUID.randomUUID();

                mockMvc.perform(post("/leagues/{id}/rounds/{round}/recap", id, 1)
                        .with(user("member").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(roundRecapService);
    }

    @Test
    void anonymousCannotGenerateRecap() throws Exception {
        mockMvc.perform(post("/leagues/{id}/rounds/{round}/recap", UUID.randomUUID(), 1)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(roundRecapService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void incompleteRoundReturnsLocalizedFlashKey() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InvalidLeagueOperationException("incomplete"))
                .when(roundRecapService).generateAllLanguages(id, 1, false);

        mockMvc.perform(post("/leagues/{id}/rounds/{round}/recap", id, 1)
                        .locale(Locale.ENGLISH)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "flash.recap.incomplete"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aiFailureReturnsLocalizedFlashKey() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("AI unavailable"))
                .when(roundRecapService).generateAllLanguages(id, 1, false);

        mockMvc.perform(post("/leagues/{id}/rounds/{round}/recap", id, 1)
                        .locale(Locale.ENGLISH)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "flash.recap.failed"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGenerateSeasonRecap() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/leagues/{id}/season-recap", id)
                        .param("regenerate", "false")
                        .locale(Locale.ENGLISH)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/leagues/" + id + "?tab=overview#overview"))
                .andExpect(flash().attribute("statusMessage", "flash.seasonrecap.generated"));

        verify(roundRecapService).generateSeasonAllLanguages(id, false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRegenerateSeasonRecap() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/leagues/{id}/season-recap", id)
                        .param("regenerate", "true")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("statusMessage", "flash.seasonrecap.regenerated"));

        verify(roundRecapService).generateSeasonAllLanguages(id, true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void seasonWithoutCompletedMatchesReturnsLocalizedFlashKey() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InvalidLeagueOperationException("incomplete"))
                .when(roundRecapService).generateSeasonAllLanguages(id, false);

        mockMvc.perform(post("/leagues/{id}/season-recap", id)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "flash.seasonrecap.incomplete"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void seasonAiFailureReturnsLocalizedFlashKey() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("AI unavailable"))
                .when(roundRecapService).generateSeasonAllLanguages(id, false);

        mockMvc.perform(post("/leagues/{id}/season-recap", id)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "flash.seasonrecap.failed"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotGenerateSeasonRecap() throws Exception {
        mockMvc.perform(post("/leagues/{id}/season-recap", UUID.randomUUID())
                        .with(user("member").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(roundRecapService);
    }

    @Test
    @WithMockUser
    void detailExposesMissingRecapForCurrentLocale() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(view().name("leagues/detail"))
                .andExpect(model().attributeDoesNotExist("roundRecap"))
                .andExpect(model().attribute("roundRecapReady", true))
                .andExpect(model().attributeDoesNotExist("seasonRecap"))
                .andExpect(model().attribute("seasonRecapReady", true));
    }

    @Test
    @WithMockUser
    void detailExposesGeneratedRecapForCurrentLocale() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        RoundRecapView recap = new RoundRecapView(
                "English recap", LocalDateTime.now(), "en", "a".repeat(64));
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.of(recap));
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.of(recap));
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attribute("roundRecap", recap))
                .andExpect(model().attribute("seasonRecap", recap))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("English recap")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void incompleteRoundKeepsRecapButtonClickable() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(false);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("admin").roles("ADMIN"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attribute("roundRecapReady", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/leagues/" + id + "/rounds/1/recap")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("disabled=\"disabled\""))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unfinishedSeasonKeepsSeasonRecapButtonClickable() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(false);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(false);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("admin").roles("ADMIN"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attribute("seasonRecapReady", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/leagues/" + id + "/season-recap")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("disabled=\"disabled\""))));
    }

    private LeagueDetailView league(UUID id) {
        MatchDto match = new MatchDto();
        match.setId(UUID.randomUUID());
        match.setRoundNumber(1);
        match.setPlayedAt(LocalDateTime.now().minusHours(2));
        match.setHomeScore(1);
        match.setAwayScore(0);
        match.setHomeTeamId(UUID.randomUUID());
        match.setAwayTeamId(UUID.randomUUID());
        match.setHomeTeamName("Alpha");
        match.setAwayTeamName("Beta");
        LeagueDetailView league = new LeagueDetailView();
        league.setId(id);
        league.setName("League");
        league.setMatches(List.of(match));
        league.setStandings(List.of());
        league.setTeams(List.of());
        league.setTopScorers(List.of());
        league.setTopAssists(List.of());
        return league;
    }
}
