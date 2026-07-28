package com.kickoffsim.scheduling;

import com.kickoffsim.model.Match;
import com.kickoffsim.model.Team;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherPrefetchSchedulerTest {

    @Mock private MatchRepository matchRepository;
    @Mock private WeatherService weatherService;

    private WeatherPrefetchScheduler scheduler;

    private Match matchFor(String city, LocalDateTime playedAt) {
        Team home = new Team();
        home.setCity(city);
        Match match = new Match();
        match.setHomeTeam(home);
        match.setPlayedAt(playedAt);
        return match;
    }

    @Test
    void prefetchUpcomingWeather_dedupesSameCityAndDate() {
        scheduler = new WeatherPrefetchScheduler(matchRepository, weatherService);
        LocalDateTime day = LocalDateTime.now().plusDays(2).withHour(15);
        List<Match> matches = List.of(
                matchFor("Sofia", day),
                matchFor("Sofia", day.plusHours(2)),
                matchFor("Plovdiv", day));
        when(matchRepository.findByDateRange(any(), any())).thenReturn(matches);
        when(weatherService.forecastFor(anyString(), any())).thenReturn(Optional.empty());

        scheduler.prefetchUpcomingWeather();

        ArgumentCaptor<String> cityCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(weatherService, times(2)).forecastFor(cityCaptor.capture(), dateCaptor.capture());
        assertThat(cityCaptor.getAllValues()).containsExactlyInAnyOrder("Sofia", "Plovdiv");
        assertThat(dateCaptor.getAllValues())
                .extracting(LocalDateTime::toLocalDate)
                .containsOnly(day.toLocalDate());
    }

    @Test
    void prefetchUpcomingWeather_skipsMatchesWithNullCity() {
        scheduler = new WeatherPrefetchScheduler(matchRepository, weatherService);
        when(matchRepository.findByDateRange(any(), any()))
                .thenReturn(List.of(matchFor(null, LocalDateTime.now().plusDays(1))));

        scheduler.prefetchUpcomingWeather();

        verify(weatherService, never()).forecastFor(anyString(), any());
    }

    @Test
    void prefetchUpcomingWeather_noMatches_doesNothing() {
        scheduler = new WeatherPrefetchScheduler(matchRepository, weatherService);
        when(matchRepository.findByDateRange(any(), any())).thenReturn(List.of());

        scheduler.prefetchUpcomingWeather();

        verify(weatherService, never()).forecastFor(anyString(), any());
    }
}
