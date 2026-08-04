package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import com.kickoffsim.dto.RoundRecapMatchData;
import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.exception.RoundRecapGenerationException;
import com.kickoffsim.service.RoundRecapAiClient;
import com.kickoffsim.web.RecapStoryParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DataDrivenRoundRecapClient implements RoundRecapAiClient {

    private static final int SEASON_SCOPE = 0;

    private final RecapStoryCatalog catalog;

    private final EditorialDesk editorialDesk;

    private final RecapValidator validator;

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
        boolean useContext = !season && data.context() != null;

        List<RecapStory> base = season
                ? catalog.seasonStories(data, locale)
                : catalog.roundStories(data, locale);
        if (useContext) {
            base = base.stream()
                    .filter(story -> story.kind() != RecapStoryKind.BIG_WIN)
                    .toList();
        }

        List<RecapStory> candidates = new ArrayList<>(base);
        if (useContext) {
            candidates.addAll(catalog.contextStories(data, locale));
        }

        List<RecapStory> arranged = editorialDesk.arrange(candidates, data.memory());
        return RecapStoryParser.serialize(validator.validate(arranged, data));
    }
}
