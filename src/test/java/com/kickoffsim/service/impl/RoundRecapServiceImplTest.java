package com.kickoffsim.service.impl;

import com.kickoffsim.dto.*;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.League;
import com.kickoffsim.model.LeagueFormat;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.RoundRecapRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.RoundRecapAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundRecapServiceImplTest {

    @Mock
    private RoundRecapRepository recapRepository;

    @Mock
    private LeagueRepository leagueRepository;

    @Mock
    private LeagueService leagueService;

    @Mock
    private RoundRecapAiClient aiClient;

    private RoundRecapServiceImpl service;
    private UUID leagueId;

    @BeforeEach
    void setUp() {
        service = new RoundRecapServiceImpl(recapRepository, leagueRepository, leagueService, aiClient);
        leagueId = UUID.randomUUID();
    }

    @Test
    void generate_successPersistsFactsFingerprintAndLocale() {
        LeagueDetailView league = completedLeague();
        League reference = new League();
        reference.setId(leagueId);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "bg"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(reference);
        when(aiClient.generate(any())).thenReturn("Оригинален обзор");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoundRecapView result = service.generate(leagueId, 1, Locale.forLanguageTag("bg"), false);

        assertThat(result.content()).isEqualTo("Оригинален обзор");
        assertThat(result.localeTag()).isEqualTo("bg");
        assertThat(result.sourceFingerprint()).hasSize(64);
        ArgumentCaptor<RoundRecapPromptData> prompt = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(prompt.capture());
        assertThat(prompt.getValue().languageName()).isEqualTo("Bulgarian");
        assertThat(prompt.getValue().matches().get(0).goals())
                .containsExactly("Alpha (Sofia), Alex Ace, minute 12, first half, assist Andy Aid, penalty");
        assertThat(prompt.getValue().standings().get(0).position()).isEqualTo(1);
    }

    @Test
    void generate_existingRecapReturnsItWithoutLeagueOrAiCall() {
        RoundRecap existing = recap("en", "MVP|40|Cached|Cached body");
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.of(existing));

        RoundRecapView result = service.generate(leagueId, 1, Locale.ENGLISH, false);

        assertThat(result.content()).isEqualTo("MVP|40|Cached|Cached body");
        assertThat(result.getLead().headline()).isEqualTo("Cached");
        verifyNoInteractions(leagueService, aiClient, leagueRepository);
        verify(recapRepository, never()).save(any());
    }

    @Test
    void generate_recapInAnOlderContentFormat_isRebuilt() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.of(recap("en", "✨ Highlights:\n- Draws: 2")));
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("MVP|40|Fresh|body");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.generate(leagueId, 1, Locale.ENGLISH, false).content())
                .isEqualTo("MVP|40|Fresh|body");
    }

    @Test
    void generateSeason_recapInAnOlderContentFormat_isRebuilt() {
        RoundRecap existing = recap("en", "✨ Highlights:\n- Draws: 2");
        existing.setRoundNumber(0);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "en"))
                .thenReturn(Optional.of(existing));
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("MVP|40|Fresh season|body");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.generateSeason(leagueId, Locale.ENGLISH, false).content())
                .isEqualTo("MVP|40|Fresh season|body");
    }

    @Test
    void generate_passesTheFormatAndTheScoringChartsToTheGenerator() {
        LeagueDetailView league = completedLeague();
        league.setFormat(LeagueFormat.TEN);
        league.setTopScorers(List.of(playerStat("Alex Ace", "Alpha", "Sofia", 9)));
        league.setTopAssists(List.of(playerStat("Andy Aid", "Beta", null, 4)));
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("MVP|40|Fresh|body");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate(leagueId, 1, Locale.ENGLISH, false);

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(captor.capture());
        assertThat(captor.getValue().matchesPerTeam()).isEqualTo(LeagueFormat.TEN.getTotalRounds());
        assertThat(captor.getValue().topScorers())
                .containsExactly(new RoundRecapPlayerData("Alex Ace", "Alpha (Sofia)", 9));
        assertThat(captor.getValue().topAssists())
                .containsExactly(new RoundRecapPlayerData("Andy Aid", "Beta", 4));
    }

    @Test
    void generate_leagueWithoutFormatOrCharts_stillBuildsThePrompt() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("MVP|40|Fresh|body");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate(leagueId, 1, Locale.ENGLISH, false);

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(captor.capture());
        assertThat(captor.getValue().matchesPerTeam()).isZero();
        assertThat(captor.getValue().topScorers()).isEmpty();
    }

    private PlayerStatRow playerStat(String player, String team, String city, int count) {
        PlayerStatRow row = new PlayerStatRow();
        row.setPlayerName(player);
        row.setTeamName(team);
        row.setTeamCity(city);
        row.setCount(count);
        return row;
    }

    @Test
    void generate_differentLocalesUseSeparateRecordsAndOriginalPrompts() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(eq(leagueId), eq(1), anyString()))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenAnswer(invocation ->
                ((RoundRecapPromptData) invocation.getArgument(0)).localeTag());
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.generate(leagueId, 1, Locale.ENGLISH, false).content()).isEqualTo("en");
        assertThat(service.generate(leagueId, 1, Locale.GERMAN, false).content()).isEqualTo("de");

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient, times(2)).generate(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoundRecapPromptData::languageName)
                .containsExactly("English", "German");
    }

    @Test
    void generateAllLanguagesCreatesSeparateOriginalRecaps() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(eq(leagueId), eq(1), anyString()))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenAnswer(invocation ->
                "Original " + ((RoundRecapPromptData) invocation.getArgument(0)).localeTag());
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generateAllLanguages(leagueId, 1, false);

        ArgumentCaptor<RoundRecapPromptData> prompts = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient, times(3)).generate(prompts.capture());
        assertThat(prompts.getAllValues()).extracting(RoundRecapPromptData::localeTag)
                .containsExactly("bg", "en", "de");
        assertThat(prompts.getAllValues()).extracting(RoundRecapPromptData::languageName)
                .containsExactly("Bulgarian", "English", "German");
        verify(recapRepository, times(3)).save(any(RoundRecap.class));
    }

    @Test
    void generateAllLanguagesKeepsEveryCachedRecap() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "bg"))
                .thenReturn(Optional.of(recap("bg", "MVP|40|BG|body")));
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.of(recap("en", "MVP|40|EN|body")));
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "de"))
                .thenReturn(Optional.of(recap("de", "MVP|40|DE|body")));

        service.generateAllLanguages(leagueId, 1, false);

        verifyNoInteractions(leagueService, leagueRepository, aiClient);
        verify(recapRepository, never()).save(any());
    }

    @Test
    void generate_regenerateReusesRowAndReplacesContentAndFingerprint() {
        RoundRecap existing = recap("de", "Alt");
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "de"))
                .thenReturn(Optional.of(existing));
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(existing.getLeague());
        when(aiClient.generate(any())).thenReturn("Neu");
        when(recapRepository.save(existing)).thenReturn(existing);

        RoundRecapView result = service.generate(leagueId, 1, Locale.GERMAN, true);

        assertThat(result.content()).isEqualTo("Neu");
        assertThat(existing.getSourceFingerprint()).hasSize(64);
        verify(aiClient).generate(any());
        verify(recapRepository).save(existing);
    }

    @Test
    void generate_incompleteRoundDoesNotCallAiOrSave() {
        LeagueDetailView league = completedLeague();
        league.getMatches().get(0).setPlayedAt(LocalDateTime.now().minusMinutes(10));
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        assertThatThrownBy(() -> service.generate(leagueId, 1, Locale.ENGLISH, false))
                .isInstanceOf(InvalidLeagueOperationException.class);
        verifyNoInteractions(aiClient);
        verify(recapRepository, never()).save(any());
    }

    @Test
    void isRoundComplete_rejectsNullTimeAndScores() {
        LeagueDetailView league = completedLeague();
        league.getMatches().get(0).setPlayedAt(null);
        assertThat(service.isRoundComplete(league, 1)).isFalse();

        league = completedLeague();
        league.getMatches().get(0).setHomeScore(null);
        assertThat(service.isRoundComplete(league, 1)).isFalse();

        league = completedLeague();
        league.getMatches().get(0).setAwayScore(null);
        assertThat(service.isRoundComplete(league, 1)).isFalse();
    }

    @Test
    void generate_invalidRoundNumberFails() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());

        assertThatThrownBy(() -> service.generate(leagueId, 0, Locale.ENGLISH, false))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void generate_missingRoundFails() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 2, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());

        assertThatThrownBy(() -> service.generate(leagueId, 2, Locale.ENGLISH, false))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void generate_missingLeaguePropagatesNotFound() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenThrow(new EntityNotFoundException("League not found"));

        assertThatThrownBy(() -> service.generate(leagueId, 1, Locale.ENGLISH, false))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void generate_aiFailureDoesNotPersist() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(aiClient.generate(any())).thenThrow(new RoundRecapGenerationException("failure"));

        assertThatThrownBy(() -> service.generate(leagueId, 1, Locale.ENGLISH, false))
                .isInstanceOf(RoundRecapGenerationException.class);
        verify(recapRepository, never()).save(any());
    }

    @Test
    void findUsesRequestedLocaleAndFallsBackUnsupportedLocaleToEnglish() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "de"))
                .thenReturn(Optional.of(recap("de", "MVP|40|Deutsch|body")));
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());

        assertThat(service.find(leagueId, 1, Locale.GERMAN)).get()
                .extracting(RoundRecapView::content).isEqualTo("MVP|40|Deutsch|body");
        assertThat(service.find(leagueId, 1, Locale.FRENCH)).isEmpty();
        assertThat(service.find(leagueId, 1, null)).isEmpty();
    }

    @Test
    void promptHandlesMissingCityMinuteAssistAndOwnGoal() {
        LeagueDetailView league = completedLeague();
        MatchDto match = league.getMatches().get(0);
        match.setHomeTeamCity("");
        GoalDto goal = match.getGoalTimeline().get(0);
        goal.setMinute(null);
        goal.setAssistantName(null);
        goal.setPenalty(false);
        goal.setOwnGoal(true);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("Recap");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate(leagueId, 1, Locale.ENGLISH, false);

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(captor.capture());
        assertThat(captor.getValue().matches().get(0).goals())
                .containsExactly("Alpha, Alex Ace, minute not recorded, first half, own goal");
    }

    @Test
    void promptMarksSecondHalfGoals() {
        LeagueDetailView league = completedLeague();
        MatchDto match = league.getMatches().get(0);
        match.getGoalTimeline().get(0).setHalf(Half.SECOND);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("Recap");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate(leagueId, 1, Locale.ENGLISH, false);

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(captor.capture());
        assertThat(captor.getValue().matches().get(0).goals())
                .containsExactly("Alpha (Sofia), Alex Ace, minute 12, second half, assist Andy Aid, penalty");
    }

    @Test
    void promptOmitsTheHalfWhenItIsNotRecorded() {
        LeagueDetailView league = completedLeague();
        MatchDto match = league.getMatches().get(0);
        match.getGoalTimeline().get(0).setHalf(null);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("Recap");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate(leagueId, 1, Locale.ENGLISH, false);

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(captor.capture());
        assertThat(captor.getValue().matches().get(0).goals())
                .containsExactly("Alpha (Sofia), Alex Ace, minute 12, assist Andy Aid, penalty");
    }

    @Test
    void promptSortsEqualKickoffsByIdAndIncludesAwayRegularGoal() {
        LeagueDetailView league = completedLeague();
        LocalDateTime kickoff = LocalDateTime.now().minusHours(2);
        MatchDto laterId = league.getMatches().get(0);
        laterId.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        laterId.setPlayedAt(kickoff);

        MatchDto earlierId = new MatchDto();
        earlierId.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        earlierId.setRoundNumber(1);
        earlierId.setPlayedAt(kickoff);
        earlierId.setHomeTeamName("Delta");
        earlierId.setAwayTeamName("Gamma");
        earlierId.setAwayTeamCity("Varna");
        earlierId.setHomeScore(0);
        earlierId.setAwayScore(1);
        GoalDto regularGoal = new GoalDto();
        regularGoal.setScorerName("Gary Goal");
        regularGoal.setMinute(7);
        regularGoal.setHomeGoal(false);
        earlierId.setGoalTimeline(List.of(regularGoal));
        league.setMatches(List.of(laterId, earlierId));

        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("Recap");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generate(leagueId, 1, Locale.ENGLISH, false);

        ArgumentCaptor<RoundRecapPromptData> captor = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(captor.capture());
        assertThat(captor.getValue().matches())
                .extracting(RoundRecapMatchData::homeTeam)
                .containsExactly("Delta", "Alpha (Sofia)");
        assertThat(captor.getValue().matches().get(0).goals())
                .containsExactly("Gamma (Varna), Gary Goal, minute 7");
    }

    @Test
    void findSeasonUsesReservedScopeAndLocale() {
        RoundRecap existing = recap("de", "MVP|40|Saison|body");
        existing.setRoundNumber(0);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "de"))
                .thenReturn(Optional.of(existing));

        assertThat(service.findSeason(leagueId, Locale.GERMAN)).get()
                .extracting(RoundRecapView::content)
                .isEqualTo("MVP|40|Saison|body");
    }

    @Test
    void findSkipsRecapsLeftOverFromAnOlderContentFormat() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 1, "en"))
                .thenReturn(Optional.of(recap("en", "✨ Highlights:\n- Draws: 2")));

        assertThat(service.find(leagueId, 1, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void findSeasonSkipsRecapsLeftOverFromAnOlderContentFormat() {
        RoundRecap existing = recap("en", "✨ Highlights:\n- Draws: 2");
        existing.setRoundNumber(0);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "en"))
                .thenReturn(Optional.of(existing));

        assertThat(service.findSeason(leagueId, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void generateSeasonReturnsCachedRecapWithoutAiCall() {
        RoundRecap existing = recap("en", "MVP|40|Cached season|body");
        existing.setRoundNumber(0);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "en"))
                .thenReturn(Optional.of(existing));

        assertThat(service.generateSeason(leagueId, Locale.ENGLISH, false).content())
                .isEqualTo("MVP|40|Cached season|body");
        verifyNoInteractions(leagueService, leagueRepository, aiClient);
        verify(recapRepository, never()).save(any());
    }

    @Test
    void generateSeasonPersistsOnlyCompletedMatchesWithSeasonScope() {
        LeagueDetailView league = completedLeague();
        MatchDto unfinished = new MatchDto();
        unfinished.setId(UUID.randomUUID());
        unfinished.setRoundNumber(2);
        unfinished.setPlayedAt(LocalDateTime.now().plusHours(1));
        unfinished.setHomeTeamName("Future");
        unfinished.setAwayTeamName("Later");
        unfinished.setHomeScore(0);
        unfinished.setAwayScore(0);
        league.setMatches(List.of(league.getMatches().get(0), unfinished));
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "bg"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenReturn("Обзор на сезона");
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoundRecapView result = service.generateSeason(
                leagueId, Locale.forLanguageTag("bg"), false);

        assertThat(result.content()).isEqualTo("Обзор на сезона");
        ArgumentCaptor<RoundRecapPromptData> prompt = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient).generate(prompt.capture());
        assertThat(prompt.getValue().roundNumber()).isZero();
        assertThat(prompt.getValue().matches()).hasSize(1);
        assertThat(prompt.getValue().matches().get(0).goals()).isEmpty();
        ArgumentCaptor<RoundRecap> recap = ArgumentCaptor.forClass(RoundRecap.class);
        verify(recapRepository).save(recap.capture());
        assertThat(recap.getValue().getRoundNumber()).isZero();
        assertThat(recap.getValue().getSourceFingerprint()).hasSize(64);
    }

    @Test
    void generateSeasonRegeneratesExistingRow() {
        RoundRecap existing = recap("en", "Old season");
        existing.setRoundNumber(0);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "en"))
                .thenReturn(Optional.of(existing));
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(existing.getLeague());
        when(aiClient.generate(any())).thenReturn("New season");
        when(recapRepository.save(existing)).thenReturn(existing);

        assertThat(service.generateSeason(leagueId, Locale.ENGLISH, true).content())
                .isEqualTo("New season");
        verify(recapRepository).save(existing);
    }

    @Test
    void generateSeasonAllLanguagesCreatesSeparateOriginalRecaps() {
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(eq(leagueId), eq(0), anyString()))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(completedLeague());
        when(leagueRepository.getReferenceById(leagueId)).thenReturn(new League());
        when(aiClient.generate(any())).thenAnswer(invocation ->
                "Original " + ((RoundRecapPromptData) invocation.getArgument(0)).localeTag());
        when(recapRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generateSeasonAllLanguages(leagueId, false);

        ArgumentCaptor<RoundRecapPromptData> prompts = ArgumentCaptor.forClass(RoundRecapPromptData.class);
        verify(aiClient, times(3)).generate(prompts.capture());
        assertThat(prompts.getAllValues()).extracting(RoundRecapPromptData::localeTag)
                .containsExactly("bg", "en", "de");
        assertThat(prompts.getAllValues()).allMatch(prompt -> prompt.roundNumber() == 0);
        verify(recapRepository, times(3)).save(any(RoundRecap.class));
    }

    @Test
    void generateSeasonBeforeEveryTeamPlayedFourMatchesFailsBeforeAiCall() {
        LeagueDetailView league = completedLeague();
        league.getStandings().get(1).setPlayed(3);
        when(recapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, 0, "en"))
                .thenReturn(Optional.empty());
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        assertThatThrownBy(() -> service.generateSeason(
                leagueId, Locale.ENGLISH, false))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("at least 4 matches");
        verifyNoInteractions(aiClient);
        verify(recapRepository, never()).save(any());
    }

    @Test
    void seasonReadinessRequiresFourMatchesFromEveryTeam() {
        LeagueDetailView league = completedLeague();
        assertThat(service.isSeasonRecapReady(league)).isTrue();

        league.getStandings().get(1).setPlayed(3);
        assertThat(service.isSeasonRecapReady(league)).isFalse();
    }

    @Test
    void seasonReadinessIsFalseWhenTheLeagueHasNoStandingsYet() {
        LeagueDetailView league = completedLeague();

        league.setStandings(List.of());
        assertThat(service.isSeasonRecapReady(league)).isFalse();

        league.setStandings(null);
        assertThat(service.isSeasonRecapReady(league)).isFalse();
    }

    private LeagueDetailView completedLeague() {
        LeagueDetailView league = new LeagueDetailView();
        league.setId(leagueId);
        league.setName("Test League");

        MatchDto match = new MatchDto();
        match.setId(UUID.randomUUID());
        match.setRoundNumber(1);
        match.setPlayedAt(LocalDateTime.now().minusHours(2));
        match.setHomeTeamName("Alpha");
        match.setHomeTeamCity("Sofia");
        match.setAwayTeamName("Beta");
        match.setHomeScore(2);
        match.setAwayScore(1);
        GoalDto goal = new GoalDto();
        goal.setScorerName("Alex Ace");
        goal.setAssistantName("Andy Aid");
        goal.setMinute(12);
        goal.setHalf(Half.FIRST);
        goal.setHomeGoal(true);
        goal.setPenalty(true);
        match.setGoalTimeline(List.of(goal));
        league.setMatches(List.of(match));

        StandingRow alpha = new StandingRow();
        alpha.setTeamName("Alpha");
        alpha.setTeamCity("Sofia");
        alpha.setPlayed(4);
        alpha.setWins(1);
        alpha.setGoalsFor(2);
        alpha.setGoalsAgainst(1);
        StandingRow beta = new StandingRow();
        beta.setTeamName("Beta");
        beta.setPlayed(4);
        beta.setLosses(1);
        beta.setGoalsFor(1);
        beta.setGoalsAgainst(2);
        league.setStandings(List.of(alpha, beta));
        return league;
    }

    private RoundRecap recap(String locale, String content) {
        League league = new League();
        league.setId(leagueId);
        RoundRecap recap = new RoundRecap();
        recap.setId(UUID.randomUUID());
        recap.setLeague(league);
        recap.setRoundNumber(1);
        recap.setLocaleTag(locale);
        recap.setContent(content);
        recap.setGeneratedAt(LocalDateTime.now());
        recap.setSourceFingerprint("a".repeat(64));
        return recap;
    }
}
