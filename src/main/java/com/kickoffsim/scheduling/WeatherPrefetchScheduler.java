package com.kickoffsim.scheduling;

import com.kickoffsim.model.Match;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherPrefetchScheduler {

    private static final int FORECAST_WINDOW_DAYS = 15;

    private final MatchRepository matchRepository;
    private final WeatherService weatherService;

    @Scheduled(cron = "0 15 */3 * * *")
    public void prefetchUpcomingWeather() {
        LocalDateTime now = LocalDateTime.now();
        List<Match> matches = matchRepository.findByDateRange(now, now.plusDays(FORECAST_WINDOW_DAYS));

        Set<CityDate> cityDates = new LinkedHashSet<>();
        for (Match match : matches) {
            String city = match.getHomeTeam().getCity();
            if (city != null) {
                cityDates.add(new CityDate(city, match.getPlayedAt().toLocalDate()));
            }
        }

        cityDates.forEach(cd -> weatherService.forecastFor(cd.city(), cd.date()));
        log.info("Prefetched weather for {} city/date combination(s)", cityDates.size());
    }

    private record CityDate(String city, LocalDate date) {}
}
