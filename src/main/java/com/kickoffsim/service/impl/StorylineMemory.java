package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapMemory;
import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.RoundRecapRepository;
import com.kickoffsim.web.RecapStoryParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StorylineMemory {

    private static final int SEASON_SCOPE = 0;

    private static final int LOOKBACK_ROUNDS = 3;

    private static final int MAX_ANGLES = 6;

    private static final int MAX_HEADLINES = 12;

    private final RoundRecapRepository roundRecapRepository;

    public RecapMemory recall(UUID leagueId, int scope, String localeTag, boolean regenerate) {
        List<RecapStory> history = new ArrayList<>();
        if (regenerate) {
            roundRecapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, scope, localeTag)
                    .ifPresent(recap -> history.addAll(RecapStoryParser.parse(recap.getContent())));
        }
        int upper = scope == SEASON_SCOPE ? Integer.MAX_VALUE : scope - 1;
        if (upper >= 1) {
            List<RoundRecap> recent = roundRecapRepository
                    .findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
                            leagueId, localeTag, 1, upper);
            recent.stream()
                    .limit(LOOKBACK_ROUNDS)
                    .forEach(recap -> history.addAll(RecapStoryParser.parse(recap.getContent())));
        }

        Set<String> angles = new LinkedHashSet<>();
        List<String> headlines = new ArrayList<>();
        for (RecapStory story : history) {
            if (story.kind().getFamily() != RecapStoryFamily.NARRATIVE) {
                continue;
            }
            if (angles.size() < MAX_ANGLES) {
                angles.add(story.kind().name());
            }
            if (headlines.size() < MAX_HEADLINES && !headlines.contains(story.headline())) {
                headlines.add(story.headline());
            }
        }
        return new RecapMemory(new ArrayList<>(angles), headlines, regenerate);
    }
}
