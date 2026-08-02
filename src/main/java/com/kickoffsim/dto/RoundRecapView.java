package com.kickoffsim.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RoundRecapView(
        String content,
        LocalDateTime generatedAt,
        String localeTag,
        String sourceFingerprint,
        List<RecapStory> stories) {

    public RecapStory getLead() {
        return narrative().stream().findFirst().orElse(null);
    }

    public List<RecapStory> getSecondary() {
        List<RecapStory> narrative = narrative();
        return narrative.isEmpty() ? List.of() : narrative.subList(1, narrative.size());
    }

    public RecapStory getStats() {
        return byFamily(RecapStoryFamily.STATS).stream().findFirst().orElse(null);
    }

    public List<RecapStory> getLists() {
        return byFamily(RecapStoryFamily.LIST);
    }

    private List<RecapStory> narrative() {
        return byFamily(RecapStoryFamily.NARRATIVE);
    }

    private List<RecapStory> byFamily(RecapStoryFamily family) {
        return stories == null ? List.of() : stories.stream()
                .filter(story -> story.kind().getFamily() == family)
                .toList();
    }
}
