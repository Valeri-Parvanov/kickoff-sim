package com.kickoffsim.web;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;

import java.util.ArrayList;
import java.util.List;

public final class RecapStoryParser {

    public static final String FIELD_SEPARATOR = "|";

    private static final String FIELD_PATTERN = "\\|";

    private static final String LINE_SEPARATOR = "\n";

    private static final int FIELDS = 4;

    private RecapStoryParser() {
    }

    public static List<RecapStory> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<RecapStory> stories = new ArrayList<>();
        for (String line : content.split(LINE_SEPARATOR)) {
            if (line.isBlank()) {
                continue;
            }
            RecapStory story = toStory(line.strip());
            if (story == null) {
                return List.of();
            }
            stories.add(story);
        }
        return List.copyOf(stories);
    }

    public static String serialize(List<RecapStory> stories) {
        StringBuilder builder = new StringBuilder();
        for (RecapStory story : stories) {
            if (!builder.isEmpty()) {
                builder.append(LINE_SEPARATOR);
            }
            builder.append(story.kind().name()).append(FIELD_SEPARATOR)
                    .append(story.weight()).append(FIELD_SEPARATOR)
                    .append(story.headline()).append(FIELD_SEPARATOR)
                    .append(story.body());
        }
        return builder.toString();
    }

    private static RecapStory toStory(String line) {
        String[] fields = line.split(FIELD_PATTERN, FIELDS);
        if (fields.length < FIELDS) {
            return null;
        }
        RecapStoryKind kind = kindOf(fields[0]);
        Integer weight = weightOf(fields[1]);
        if (kind == null || weight == null) {
            return null;
        }
        return new RecapStory(kind, weight, fields[2], fields[3]);
    }

    private static RecapStoryKind kindOf(String value) {
        for (RecapStoryKind kind : RecapStoryKind.values()) {
            if (kind.name().equals(value)) {
                return kind;
            }
        }
        return null;
    }

    private static Integer weightOf(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
