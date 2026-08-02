package com.kickoffsim.dto;

import java.util.Arrays;
import java.util.List;

public record RecapStory(RecapStoryKind kind, int weight, String headline, String body) {

    public static final String ITEM_SEPARATOR = ";;";

    public List<String> items() {
        return body == null || body.isBlank()
                ? List.of()
                : Arrays.stream(body.split(ITEM_SEPARATOR))
                        .map(String::strip)
                        .filter(item -> !item.isEmpty())
                        .toList();
    }
}
