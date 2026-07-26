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

    @NotNull(message = "{validation.goal.scorer.required}")
    private UUID scorerId;

    private UUID assistantId;

    @NotNull(message = "{validation.goal.minute.required}")
    @Min(value = 1, message = "{validation.goal.minute.min}")
    @Max(value = 40, message = "{validation.goal.minute.max}")
    private Integer minute;

    private boolean ownGoal;
    private boolean penalty;
}
