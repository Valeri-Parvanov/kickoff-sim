package com.kickoffsim.dto;

import com.kickoffsim.model.Half;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class GoalDto {

    private UUID id;
    private UUID scorerId;
    private UUID assistantId;
    private String scorerName;
    private String assistantName;
    private Integer minute;
    private Integer offsetSeconds;
    private Half half;
    private UUID teamId;
    private Integer runningHomeScore;
    private Integer runningAwayScore;
    private boolean homeGoal;
    private boolean firstInHalf;
    private boolean ownGoal;
    private boolean penalty;
}
