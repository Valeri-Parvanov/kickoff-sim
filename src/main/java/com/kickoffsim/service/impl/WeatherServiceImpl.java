package com.kickoffsim.service.impl;

import com.kickoffsim.dto.WeatherDayDto;
import com.kickoffsim.dto.WeatherForecastDto;
import com.kickoffsim.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    private static final int MAX_FORECAST_DAYS = 15;
    private static final int MAX_PAST_DAYS = 7;

    private final OpenMeteoDayClient dayClient;

    @Override
    public Optional<WeatherForecastDto> forecastFor(String city, LocalDateTime kickoff) {
        if (city == null || kickoff == null) {
            return Optional.empty();
        }
        LocalDate date = kickoff.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.isBefore(today.minusDays(MAX_PAST_DAYS)) || date.isAfter(today.plusDays(MAX_FORECAST_DAYS))) {
            return Optional.empty();
        }
        return dayClient.fetchDay(city, date)
                .flatMap(day -> atHour(day, kickoff));
    }

    private Optional<WeatherForecastDto> atHour(WeatherDayDto day, LocalDateTime kickoff) {
        int hour = kickoff.getHour();
        Double temp = valueAt(day.getHourlyTempC(), hour);
        if (temp == null) {
            return Optional.empty();
        }
        return Optional.of(new WeatherForecastDto(
                kickoff.withMinute(0).withSecond(0).withNano(0),
                temp,
                valueAt(day.getHourlyPrecipitationProbability(), hour)));
    }

    private <T> T valueAt(List<T> values, int hour) {
        return values != null && hour < values.size() ? values.get(hour) : null;
    }
}
