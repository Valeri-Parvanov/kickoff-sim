package com.kickoffsim.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class GoalEventDto {

    @NotNull(message = "Please select the scorer")
    private UUID scorerId;

    private UUID assistantId;

    @NotNull(message = "Minute is required")
    @Min(value = 1, message = "Minute must be at least 1")
    @Max(value = 40, message = "Minute must be at most 40")
    private Integer minute;

    private boolean ownGoal;
    private boolean penalty;
}
