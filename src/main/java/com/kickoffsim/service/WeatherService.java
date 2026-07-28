package com.kickoffsim.service;

import com.kickoffsim.dto.WeatherForecastDto;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WeatherService {

    Optional<WeatherForecastDto> forecastFor(String city, LocalDateTime kickoff);
}
