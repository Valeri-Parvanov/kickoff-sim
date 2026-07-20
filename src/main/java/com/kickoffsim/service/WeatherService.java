package com.kickoffsim.service;

import com.kickoffsim.dto.WeatherForecastDto;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherService {

    Optional<WeatherForecastDto> forecastFor(String city, LocalDate date);
}
