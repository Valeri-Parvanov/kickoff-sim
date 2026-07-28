package com.kickoffsim.service.impl;

import com.kickoffsim.dto.WeatherDayDto;
import com.kickoffsim.dto.WeatherForecastDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherServiceImplTest {

    private OpenMeteoDayClient dayClient;
    private WeatherServiceImpl weatherService;

    @BeforeEach
    void setUp() {
        dayClient = Mockito.mock(OpenMeteoDayClient.class);
        weatherService = new WeatherServiceImpl(dayClient);
    }

    private WeatherDayDto day(LocalDate date) {
        List<Double> temps = new ArrayList<>();
        List<Integer> rain = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            temps.add(10.0 + hour);
            rain.add(hour);
        }
        return new WeatherDayDto(date, temps, rain);
    }

    @Test
    void forecastFor_returnsEmpty_whenCityIsNull() {
        assertThat(weatherService.forecastFor(null, LocalDateTime.now().plusDays(1))).isEmpty();
        verify(dayClient, never()).fetchDay(any(), any());
    }

    @Test
    void forecastFor_returnsEmpty_whenKickoffIsNull() {
        assertThat(weatherService.forecastFor("Sofia", null)).isEmpty();
        verify(dayClient, never()).fetchDay(any(), any());
    }

    @Test
    void forecastFor_returnsEmpty_whenDateTooFarInPast() {
        assertThat(weatherService.forecastFor("Sofia", LocalDateTime.now().minusDays(8))).isEmpty();
        verify(dayClient, never()).fetchDay(any(), any());
    }

    @Test
    void forecastFor_returnsEmpty_whenDateTooFarInFuture() {
        assertThat(weatherService.forecastFor("Sofia", LocalDateTime.now().plusDays(30))).isEmpty();
        verify(dayClient, never()).fetchDay(any(), any());
    }

    @Test
    void forecastFor_returnsEmpty_whenDayLookupFails() {
        LocalDateTime kickoff = LocalDate.now().plusDays(1).atTime(15, 15);
        when(dayClient.fetchDay("Sofia", kickoff.toLocalDate())).thenReturn(Optional.empty());

        assertThat(weatherService.forecastFor("Sofia", kickoff)).isEmpty();
    }

    @Test
    void forecastFor_returnsTemperatureAtKickoffHour() {
        LocalDateTime kickoff = LocalDate.now().plusDays(1).atTime(15, 15);
        when(dayClient.fetchDay("Sofia", kickoff.toLocalDate()))
                .thenReturn(Optional.of(day(kickoff.toLocalDate())));

        Optional<WeatherForecastDto> result = weatherService.forecastFor("Sofia", kickoff);

        assertThat(result).isPresent();
        assertThat(result.get().getTempC()).isEqualTo(25.0);
        assertThat(result.get().getPrecipitationProbability()).isEqualTo(15);
        assertThat(result.get().getTime()).isEqualTo(kickoff.withMinute(0).withSecond(0).withNano(0));
    }

    @Test
    void forecastFor_worksForRecentlyPlayedMatches() {
        LocalDateTime kickoff = LocalDate.now().minusDays(3).atTime(9, 0);
        when(dayClient.fetchDay("Sofia", kickoff.toLocalDate()))
                .thenReturn(Optional.of(day(kickoff.toLocalDate())));

        Optional<WeatherForecastDto> result = weatherService.forecastFor("Sofia", kickoff);

        assertThat(result).isPresent();
        assertThat(result.get().getTempC()).isEqualTo(19.0);
    }

    @Test
    void forecastFor_returnsEmpty_whenHourMissingFromSeries() {
        LocalDateTime kickoff = LocalDate.now().plusDays(1).atTime(23, 0);
        when(dayClient.fetchDay("Sofia", kickoff.toLocalDate()))
                .thenReturn(Optional.of(new WeatherDayDto(
                        kickoff.toLocalDate(), List.of(12.0, 13.0), List.of(0, 0))));

        assertThat(weatherService.forecastFor("Sofia", kickoff)).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenHourlyTemperatureIsNull() {
        LocalDateTime kickoff = LocalDate.now().plusDays(1).atTime(1, 0);
        List<Double> temps = new ArrayList<>(Collections.nCopies(24, null));
        when(dayClient.fetchDay("Sofia", kickoff.toLocalDate()))
                .thenReturn(Optional.of(new WeatherDayDto(
                        kickoff.toLocalDate(), temps, Collections.nCopies(24, 0))));

        assertThat(weatherService.forecastFor("Sofia", kickoff)).isEmpty();
    }

    @Test
    void forecastFor_returnsTemperature_whenPrecipitationSeriesMissing() {
        LocalDateTime kickoff = LocalDate.now().plusDays(1).atTime(LocalTime.of(8, 45));
        when(dayClient.fetchDay("Sofia", kickoff.toLocalDate()))
                .thenReturn(Optional.of(new WeatherDayDto(
                        kickoff.toLocalDate(), day(kickoff.toLocalDate()).getHourlyTempC(), null)));

        Optional<WeatherForecastDto> result = weatherService.forecastFor("Sofia", kickoff);

        assertThat(result).isPresent();
        assertThat(result.get().getTempC()).isEqualTo(18.0);
        assertThat(result.get().getPrecipitationProbability()).isNull();
    }
}
