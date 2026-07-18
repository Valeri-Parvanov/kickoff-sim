package com.kickoffsim.web;

import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.SquadForm;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SquadRowValidatorTest {

    private static PlayerRowDto row(String firstName, String lastName, Integer shirtNumber) {
        PlayerRowDto row = new PlayerRowDto();
        row.setFirstName(firstName);
        row.setLastName(lastName);
        row.setShirtNumber(shirtNumber);
        return row;
    }

    private static BindingResult binding(List<PlayerRowDto> rows) {
        SquadForm form = new SquadForm();
        form.setRows(rows);
        return new BeanPropertyBindingResult(form, "squadForm");
    }

    @Test
    void validate_emptyRow_isSkippedEntirely() {
        List<PlayerRowDto> rows = List.of(row(null, null, null));
        BindingResult br = binding(rows);

        List<Integer> filled = SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(filled).isEmpty();
        assertThat(br.hasErrors()).isFalse();
    }

    @Test
    void validate_blankNames_areTreatedAsEmpty() {
        List<PlayerRowDto> rows = List.of(row("  ", "  ", null));
        BindingResult br = binding(rows);

        List<Integer> filled = SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(filled).isEmpty();
    }

    @Test
    void validate_missingFirstName_rejectsField() {
        List<PlayerRowDto> rows = List.of(row(null, "Ivanov", 5));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].firstName")).isTrue();
    }

    @Test
    void validate_blankFirstName_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("   ", "Ivanov", 5));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].firstName")).isTrue();
    }

    @Test
    void validate_missingLastName_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("Ivan", null, 5));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].lastName")).isTrue();
    }

    @Test
    void validate_blankLastName_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "   ", 5));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].lastName")).isTrue();
    }

    @Test
    void validate_missingShirtNumber_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "Ivanov", null));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].shirtNumber")).isTrue();
    }

    @Test
    void validate_shirtNumberTooLow_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "Ivanov", 0));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].shirtNumber")).isTrue();
    }

    @Test
    void validate_shirtNumberTooHigh_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "Ivanov", 100));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].shirtNumber")).isTrue();
    }

    @Test
    void validate_shirtNumberTaken_rejectsField() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "Ivanov", 7));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(7), br);

        assertThat(br.hasFieldErrors("rows[0].shirtNumber")).isTrue();
    }

    @Test
    void validate_duplicateShirtNumberInBatch_rejectsSecondOccurrence() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "Ivanov", 7), row("Georgi", "Petrov", 7));
        BindingResult br = binding(rows);

        SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(br.hasFieldErrors("rows[0].shirtNumber")).isFalse();
        assertThat(br.hasFieldErrors("rows[1].shirtNumber")).isTrue();
    }

    @Test
    void validate_validRow_noErrorsAndIncludedInFilledRows() {
        List<PlayerRowDto> rows = List.of(row("Ivan", "Ivanov", 7));
        BindingResult br = binding(rows);

        List<Integer> filled = SquadRowValidator.validate(rows, "rows", Set.of(), br);

        assertThat(filled).containsExactly(0);
        assertThat(br.hasErrors()).isFalse();
    }

    @Test
    void toPlayers_mapsAndTrimsFilledRows() {
        List<PlayerRowDto> rows = List.of(row("  Ivan  ", "  Ivanov  ", 7), row(null, null, null));

        List<PlayerDto> players = SquadRowValidator.toPlayers(rows, List.of(0));

        assertThat(players).hasSize(1);
        assertThat(players.get(0).getFirstName()).isEqualTo("Ivan");
        assertThat(players.get(0).getLastName()).isEqualTo("Ivanov");
        assertThat(players.get(0).getShirtNumber()).isEqualTo(7);
    }
}
