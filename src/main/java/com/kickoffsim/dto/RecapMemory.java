package com.kickoffsim.dto;

import java.util.List;

public record RecapMemory(
        List<String> recentAngles,
        List<String> recentHeadlines,
        boolean regeneration) {

    public RecapMemory {
        recentAngles = recentAngles == null ? List.of() : List.copyOf(recentAngles);
        recentHeadlines = recentHeadlines == null ? List.of() : List.copyOf(recentHeadlines);
    }

    public static RecapMemory empty() {
        return new RecapMemory(List.of(), List.of(), false);
    }

    public int angleRecency(String angle) {
        int index = recentAngles.indexOf(angle);
        return index < 0 ? -1 : recentAngles.size() - index;
    }

    public boolean hasHeadline(String headline) {
        return recentHeadlines.contains(headline);
    }
}
