package com.kickoffsim.web;

import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.PlayerRowDto;
import org.springframework.validation.BindingResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SquadRowValidator {

    private SquadRowValidator() {
    }

    public static List<Integer> validate(List<PlayerRowDto> rows, String prefix,
                                         Set<Integer> takenNumbers, BindingResult bindingResult) {
        List<Integer> filledRows = new ArrayList<>();
        Set<Integer> batchNumbers = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            PlayerRowDto row = rows.get(i);
            if (row.isEmpty()) {
                continue;
            }
            filledRows.add(i);
            String base = prefix + "[" + i + "].";

            if (row.getFirstName() == null || row.getFirstName().isBlank()) {
                bindingResult.rejectValue(base + "firstName", "validation.player.firstname.required");
            }
            if (row.getLastName() == null || row.getLastName().isBlank()) {
                bindingResult.rejectValue(base + "lastName", "validation.player.lastname.required");
            }

            Integer number = row.getShirtNumber();
            if (number == null) {
                bindingResult.rejectValue(base + "shirtNumber", "validation.player.shirt.required");
            } else if (number < 1 || number > 99) {
                bindingResult.rejectValue(base + "shirtNumber", "validation.player.shirt.range");
            } else if (takenNumbers.contains(number)) {
                bindingResult.rejectValue(base + "shirtNumber", "validation.player.shirt.taken",
                        new Object[]{number}, null);
            } else if (!batchNumbers.add(number)) {
                bindingResult.rejectValue(base + "shirtNumber", "validation.player.shirt.duplicate",
                        new Object[]{number}, null);
            }
        }
        return filledRows;
    }

    public static void autoFillShirtNumbers(List<PlayerRowDto> rows, Set<Integer> takenNumbers, int remaining) {
        int next = 1;
        while (rows.size() < remaining) {
            PlayerRowDto row = new PlayerRowDto();
            while (next <= 99 && takenNumbers.contains(next)) next++;
            if (next <= 99) {
                row.setShirtNumber(next);
                takenNumbers.add(next++);
            }
            rows.add(row);
        }
    }

    public static List<PlayerDto> toPlayers(List<PlayerRowDto> rows, List<Integer> filledRows) {
        List<PlayerDto> players = new ArrayList<>();
        for (int index : filledRows) {
            PlayerRowDto row = rows.get(index);
            PlayerDto dto = new PlayerDto();
            dto.setId(row.getId());
            dto.setFirstName(row.getFirstName().trim());
            dto.setLastName(row.getLastName().trim());
            dto.setShirtNumber(row.getShirtNumber());
            players.add(dto);
        }
        return players;
    }
}
