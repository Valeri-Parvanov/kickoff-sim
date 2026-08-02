package com.kickoffsim.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record RecapStory(RecapStoryKind kind, int weight, String headline, String body) {

    public static final String ITEM_SEPARATOR = ";;";

    public static final String FIELD_SEPARATOR = "::";

    private static final int PLAYER_FIELDS = 4;

    private static final int PAIR_FIELDS = 2;

    private static final int[] LINES = {1, 2, 2, 1};

    public List<String> items() {
        return body == null || body.isBlank()
                ? List.of()
                : Arrays.stream(body.split(ITEM_SEPARATOR))
                        .map(String::strip)
                        .filter(item -> !item.isEmpty())
                        .toList();
    }

    public List<RecapPlayer> players() {
        return items().stream().map(RecapStory::toPlayer).toList();
    }

    public List<RecapStat> tiles() {
        return items().stream().map(RecapStory::toStat).toList();
    }

    public List<RecapLink> results() {
        return items().stream().map(RecapStory::toLink).toList();
    }

    public List<List<RecapPlayer>> lineup() {
        List<RecapPlayer> players = players();
        List<List<RecapPlayer>> lines = new ArrayList<>();
        int index = 0;
        for (int size : LINES) {
            if (index >= players.size()) {
                return List.copyOf(lines);
            }
            int end = Math.min(index + size, players.size());
            lines.add(players.subList(index, end));
            index = end;
        }
        if (index < players.size()) {
            lines.add(players.subList(index, players.size()));
        }
        return List.copyOf(lines);
    }

    private static RecapPlayer toPlayer(String item) {
        String[] fields = item.split(FIELD_SEPARATOR);
        if (fields.length < PLAYER_FIELDS) {
            return new RecapPlayer(item, "", 0, 0);
        }
        return new RecapPlayer(fields[0], fields[1], number(fields[2]), number(fields[3]));
    }

    private static RecapStat toStat(String item) {
        String[] fields = item.split(FIELD_SEPARATOR);
        return fields.length < PAIR_FIELDS
                ? new RecapStat("", item)
                : new RecapStat(fields[0], fields[1]);
    }

    private static RecapLink toLink(String item) {
        String[] fields = item.split(FIELD_SEPARATOR);
        return fields.length < PAIR_FIELDS
                ? new RecapLink(item, null)
                : new RecapLink(fields[0], fields[1]);
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
