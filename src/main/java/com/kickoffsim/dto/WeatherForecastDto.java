package com.kickoffsim.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class WeatherForecastDto {

    private final LocalDate date;
    private final Double maxTempC;
    private final Double minTempC;
    private final Integer precipitationProbability;

    public WeatherForecastDto(LocalDate date, Double maxTempC, Double minTempC, Integer precipitationProbability) {
        this.date = date;
        this.maxTempC = maxTempC;
        this.minTempC = minTempC;
        this.precipitationProbability = precipitationProbability;
    }
}
