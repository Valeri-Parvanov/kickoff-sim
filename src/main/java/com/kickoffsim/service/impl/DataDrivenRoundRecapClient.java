package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryFamily;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.service.RoundRecapAiClient;
import com.kickoffsim.web.RecapStoryParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DataDrivenRoundRecapClient implements RoundRecapAiClient {

    private static final int SEASON_SCOPE = 0;

    private static final int MAX_NARRATIVE_STORIES = 6;

    private final RecapStoryCatalog catalog;

    @Override
    public String generate(RoundRecapPromptData data) {
        if (data == null) {
            throw new RoundRecapGenerationException("No data was supplied for the recap.");
        }
        List<RoundRecapMatchData> matches = data.matches() == null ? List.of() : data.matches();
        if (matches.isEmpty()) {
            throw new RoundRecapGenerationException("There are no played matches to analyse.");
        }

        Locale locale = Locale.forLanguageTag(data.localeTag() == null ? "en" : data.localeTag());
        boolean season = data.roundNumber() == SEASON_SCOPE;
        List<RecapStory> candidates = season
                ? catalog.seasonStories(data, locale)
                : catalog.roundStories(data, locale);

        return RecapStoryParser.serialize(arrange(candidates));
    }

    private List<RecapStory> arrange(List<RecapStory> candidates) {
        List<RecapStory> narrative = byFamily(candidates, RecapStoryFamily.NARRATIVE).stream()
                .sorted(Comparator.comparingInt(RecapStory::weight).reversed()
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

    private List<RecapStory> byFamily(List<RecapStory> candidates, RecapStoryFamily family) {
        return candidates.stream()
                .filter(story -> story.kind().getFamily() == family)
                .toList();
    }
}
