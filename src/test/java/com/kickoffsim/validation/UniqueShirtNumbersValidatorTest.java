package com.kickoffsim.validation;

import com.kickoffsim.dto.PlayerDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueShirtNumbersValidatorTest {

    private final UniqueShirtNumbersValidator validator = new UniqueShirtNumbersValidator();

    @Test
    void nullList_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void emptyList_isValid() {
        assertThat(validator.isValid(List.of(), null)).isTrue();
    }

    @Test
    void distinctShirtNumbers_areValid() {
        assertThat(validator.isValid(List.of(player(1), player(2)), null)).isTrue();
    }

    @Test
    void repeatedShirtNumber_isInvalid() {
        assertThat(validator.isValid(List.of(player(5), player(5)), null)).isFalse();
    }

    @Test
    void nullShirtNumbers_areIgnored() {
        assertThat(validator.isValid(List.of(player(null), player(null)), null)).isTrue();
    }

    private PlayerDto player(Integer shirtNumber) {
        PlayerDto player = new PlayerDto();
        player.setShirtNumber(shirtNumber);
        return player;
    }
}
