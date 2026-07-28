package com.kickoffsim.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WeatherForecastDto {

    private final LocalDateTime time;
    private final Double tempC;
    private final Integer precipitationProbability;

    public WeatherForecastDto(LocalDateTime time, Double tempC, Integer precipitationProbability) {
        this.time = time;
        this.tempC = tempC;
        this.precipitationProbability = precipitationProbability;
    }
}
