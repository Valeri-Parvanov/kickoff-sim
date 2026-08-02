package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.RoundRecapView;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.service.*;
import com.kickoffsim.web.MatchFollowSupport;
import com.kickoffsim.web.RecapStoryParser;
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
        RoundRecapView recap = recapView("MVP|40|English recap|Petar ran the round.");
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
    void missingRecapsOfferNoManualGeneration() throws Exception {
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
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/leagues/" + id + "/rounds/1/recap"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/leagues/" + id + "/season-recap"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void generatedRecapsOfferRegenerationToAdmins() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        RoundRecapView recap = recapView("RESULTS|5|Results|Alpha 1:0 Beta");
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.of(recap));
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.of(recap));
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("admin").roles("ADMIN"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/leagues/" + id + "/rounds/1/recap")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/leagues/" + id + "/season-recap")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recap-panel")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("disabled=\"disabled\""))));
    }

    @Test
    @WithMockUser
    void teamOfTheRoundIsDrawnOnAPitchAndResultsLinkToTheirMatch() throws Exception {
        UUID id = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        LeagueDetailView league = league(id);
        RoundRecapView recap = recapView("""
                SQUAD|10|Team of the round|Petar Ivanov::Alpha::2::1;;Georgi Dimitrov::Beta::1::0
                BENCH|9|Bench|Stefan Kolev::Gama::1::0
                RESULTS|5|Results|Alpha 1:0 Beta::%s""".formatted(matchId));
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.of(recap));
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(false);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recap-pitch")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recap-slot-star")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PI")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Petar Ivanov")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recap-bench-player")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stefan Kolev")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/matches/" + matchId)))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("2::1"))));
    }

    @Test
    @WithMockUser
    void missingRoundRecapIsGeneratedOnDemandAndRefreshesTheSeason() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        RoundRecapView roundRecap = recapView("Round recap");
        RoundRecapView seasonRecap = recapView("Season recap");
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.generate(id, 1, Locale.ENGLISH, false)).thenReturn(roundRecap);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(roundRecapService.generateSeason(id, Locale.ENGLISH, true)).thenReturn(seasonRecap);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attribute("roundRecap", roundRecap))
                .andExpect(model().attribute("seasonRecap", seasonRecap));

        verify(roundRecapService).generate(id, 1, Locale.ENGLISH, false);
        verify(roundRecapService).generateSeason(id, Locale.ENGLISH, true);
    }

    @Test
    @WithMockUser
    void storedRecapsAreNotRegeneratedOnDemand() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        RoundRecapView recap = recapView("Stored recap");
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
                .andExpect(status().isOk());

        verify(roundRecapService, never()).generate(any(), anyInt(), any(), anyBoolean());
        verify(roundRecapService, never()).generateSeason(any(), any(), anyBoolean());
    }

    @Test
    @WithMockUser
    void unfinishedRoundIsNotGeneratedOnDemand() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(false);
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.of(recapView("Season")));
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(false);
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("roundRecap"));

        verify(roundRecapService, never()).generate(any(), anyInt(), any(), anyBoolean());
        verify(roundRecapService, never()).generateSeason(any(), any(), anyBoolean());
    }

    @Test
    @WithMockUser
    void failedOnDemandGenerationKeepsThePlaceholder() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.generate(id, 1, Locale.ENGLISH, false))
                .thenThrow(new InvalidLeagueOperationException("incomplete"));
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(roundRecapService.generateSeason(id, Locale.ENGLISH, true))
                .thenThrow(new IllegalStateException("no data"));
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("roundRecap"))
                .andExpect(model().attributeDoesNotExist("seasonRecap"));
    }

    @Test
    @WithMockUser
    void failedSeasonRefreshKeepsTheStoredSeasonRecap() throws Exception {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = league(id);
        RoundRecapView storedSeason = recapView("Stored season");
        when(leagueService.findDetail(id)).thenReturn(league);
        when(roundRecapService.find(id, 1, Locale.ENGLISH)).thenReturn(Optional.empty());
        when(roundRecapService.isRoundComplete(league, 1)).thenReturn(true);
        when(roundRecapService.generate(id, 1, Locale.ENGLISH, false)).thenReturn(recapView("Round"));
        when(roundRecapService.findSeason(id, Locale.ENGLISH)).thenReturn(Optional.of(storedSeason));
        when(roundRecapService.isSeasonRecapReady(league)).thenReturn(true);
        when(roundRecapService.generateSeason(id, Locale.ENGLISH, true))
                .thenThrow(new IllegalStateException("no data"));
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());

        mockMvc.perform(get("/leagues/{id}", id)
                        .param("round", "1")
                        .with(user("member").roles("USER"))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(model().attribute("seasonRecap", storedSeason));
    }

    private RoundRecapView recapView(String content) {
        return new RoundRecapView(content, LocalDateTime.now(), "en", "a".repeat(64),
                RecapStoryParser.parse(content));
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
