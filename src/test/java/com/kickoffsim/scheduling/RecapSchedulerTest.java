package com.kickoffsim.scheduling;

import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.RoundRecapRepository;
import com.kickoffsim.service.RoundRecapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecapSchedulerTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private RoundRecapRepository roundRecapRepository;

    @Mock
    private RoundRecapService roundRecapService;

    private RecapScheduler scheduler;
    private UUID leagueId;

    @BeforeEach
    void setUp() {
        scheduler = new RecapScheduler(matchRepository, roundRecapRepository, roundRecapService);
        leagueId = UUID.randomUUID();
    }

    @Test
    void generateMissingRecaps_noCompletedRoundsSkipsEverything() {
        when(matchRepository.findCompletedRounds(any())).thenReturn(List.of());

        scheduler.generateMissingRecaps();

        verifyNoInteractions(roundRecapRepository, roundRecapService);
    }

    @Test
    void generateMissingRecaps_generatesRoundAndRefreshesTheSeason() {
        when(matchRepository.findCompletedRounds(any()))
                .thenReturn(List.of(new Object[]{leagueId, 2}, new Object[]{leagueId, 3}));
        when(roundRecapRepository.findScopesByLocale("en")).thenReturn(List.of());

        scheduler.generateMissingRecaps();

        verify(roundRecapService).generateAllLanguages(leagueId, 2, false);
        verify(roundRecapService).generateAllLanguages(leagueId, 3, false);
        verify(roundRecapService, times(1)).generateSeasonAllLanguages(leagueId, true);
    }

    @Test
    void generateMissingRecaps_looksBackPastTheFinalWhistle() {
        when(matchRepository.findCompletedRounds(any())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        scheduler.generateMissingRecaps();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(matchRepository).findCompletedRounds(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(before.minusMinutes(45));
    }

    @Test
    void generateMissingRecaps_skipsRoundsThatAlreadyHaveARecap() {
        when(matchRepository.findCompletedRounds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{leagueId, 2}));
        when(roundRecapRepository.findScopesByLocale("en"))
                .thenReturn(List.<Object[]>of(new Object[]{leagueId, 2}));

        scheduler.generateMissingRecaps();

        verify(roundRecapService, never()).generateAllLanguages(any(), anyInt(), anyBoolean());
        verify(roundRecapService, never()).generateSeasonAllLanguages(any(), anyBoolean());
    }

    @Test
    void generateMissingRecaps_ignoresUnusableScopes() {
        when(matchRepository.findCompletedRounds(any())).thenReturn(List.of(
                new Object[]{null, 2},
                new Object[]{leagueId, null},
                new Object[]{leagueId, 0}));
        when(roundRecapRepository.findScopesByLocale("en")).thenReturn(List.of());

        scheduler.generateMissingRecaps();

        verify(roundRecapService, never()).generateAllLanguages(any(), anyInt(), anyBoolean());
        verify(roundRecapService, never()).generateSeasonAllLanguages(any(), anyBoolean());
    }

    @Test
    void generateMissingRecaps_failedRoundDoesNotStopTheOthersOrTouchTheSeason() {
        UUID healthyLeagueId = UUID.randomUUID();
        when(matchRepository.findCompletedRounds(any()))
                .thenReturn(List.of(new Object[]{leagueId, 2}, new Object[]{healthyLeagueId, 4}));
        when(roundRecapRepository.findScopesByLocale("en")).thenReturn(List.of());
        doThrow(new InvalidLeagueOperationException("not finished"))
                .when(roundRecapService).generateAllLanguages(leagueId, 2, false);

        scheduler.generateMissingRecaps();

        verify(roundRecapService).generateAllLanguages(healthyLeagueId, 4, false);
        verify(roundRecapService).generateSeasonAllLanguages(healthyLeagueId, true);
        verify(roundRecapService, never()).generateSeasonAllLanguages(eq(leagueId), anyBoolean());
    }

    @Test
    void generateMissingRecaps_failedSeasonRecapIsSwallowed() {
        when(matchRepository.findCompletedRounds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{leagueId, 2}));
        when(roundRecapRepository.findScopesByLocale("en")).thenReturn(List.of());
        doThrow(new InvalidLeagueOperationException("no matches"))
                .when(roundRecapService).generateSeasonAllLanguages(leagueId, true);

        scheduler.generateMissingRecaps();

        verify(roundRecapService).generateAllLanguages(leagueId, 2, false);
        verify(roundRecapService).generateSeasonAllLanguages(leagueId, true);
    }
}
