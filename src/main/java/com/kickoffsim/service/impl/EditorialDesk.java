package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapMemory;
import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class EditorialDesk {

    private static final int MAX_NARRATIVE_STORIES = 6;

    private static final int ANGLE_PENALTY_STEP = 8;

    private static final int HEADLINE_PENALTY = 40;

    public List<RecapStory> arrange(List<RecapStory> candidates, RecapMemory memory) {
        RecapMemory recent = memory == null ? RecapMemory.empty() : memory;

        List<RecapStory> narrative = byFamily(candidates, RecapStoryFamily.NARRATIVE).stream()
                .sorted(Comparator.comparingInt((RecapStory story) -> priority(story, recent)).reversed()
                        .thenComparing(story -> story.kind().name()))
                .limit(MAX_NARRATIVE_STORIES)
                .toList();

        List<RecapStory> arranged = new ArrayList<>(narrative);
        arranged.addAll(byFamily(candidates, RecapStoryFamily.STATS));
        arranged.addAll(byFamily(candidates, RecapStoryFamily.LIST).stream()
                .sorted(Comparator.comparingInt(RecapStory::weight).reversed())
                .toList());
        return arranged;
    }

    private int priority(RecapStory story, RecapMemory memory) {
        int penalty = 0;
        int recency = memory.angleRecency(story.kind().name());
        if (recency >= 0) {
            penalty += recency * ANGLE_PENALTY_STEP;
        }
        if (memory.hasHeadline(story.headline())) {
            penalty += HEADLINE_PENALTY;
        }
        return story.weight() - penalty;
    }

    private List<RecapStory> byFamily(List<RecapStory> candidates, RecapStoryFamily family) {
        return candidates.stream()
                .filter(story -> story.kind().getFamily() == family)
                .toList();
    }
}
