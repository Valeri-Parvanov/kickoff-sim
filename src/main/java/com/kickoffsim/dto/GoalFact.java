package com.kickoffsim.dto;

import com.kickoffsim.model.Half;

public record GoalFact(
        boolean homeGoal,
        String team,
        String scorer,
        String assistant,
        Integer minute,
        Half half,
        boolean penalty,
        boolean ownGoal) {

    public boolean hasAssist() {
        return assistant != null && !assistant.isBlank();
    }

    public boolean secondHalf() {
        return half == Half.SECOND;
    }
}
