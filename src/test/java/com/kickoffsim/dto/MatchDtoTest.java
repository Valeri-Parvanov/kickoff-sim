package com.kickoffsim.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MatchDtoTest {

    @Test
    void isPlayedAtTimeValid_nullPlayedAt_isValid() {
        MatchDto dto = new MatchDto();

        assertThat(dto.isPlayedAtTimeValid()).isTrue();
    }

    @Test
    void isPlayedAtTimeValid_beforeEightAm_isInvalid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 7, 30));

        assertThat(dto.isPlayedAtTimeValid()).isFalse();
    }

    @Test
    void isPlayedAtTimeValid_afterElevenThirtyPm_isInvalid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 23, 45));

        assertThat(dto.isPlayedAtTimeValid()).isFalse();
    }

    @Test
    void isPlayedAtTimeValid_notOnHalfHour_isInvalid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 18, 15));

        assertThat(dto.isPlayedAtTimeValid()).isFalse();
    }

    @Test
    void isPlayedAtTimeValid_onTheHour_isValid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 18, 0));

        assertThat(dto.isPlayedAtTimeValid()).isTrue();
    }

    @Test
    void isPlayedAtTimeValid_onTheHalfHour_isValid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 18, 30));

        assertThat(dto.isPlayedAtTimeValid()).isTrue();
    }

    @Test
    void isPlayedAtTimeValid_boundaryEightAm_isValid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 8, 0));

        assertThat(dto.isPlayedAtTimeValid()).isTrue();
    }

    @Test
    void isPlayedAtTimeValid_boundaryElevenThirtyPm_isValid() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 23, 30));

        assertThat(dto.isPlayedAtTimeValid()).isTrue();
    }

    @Test
    void getPlayedAtUtcIso_nullPlayedAt_returnsEmptyString() {
        MatchDto dto = new MatchDto();

        assertThat(dto.getPlayedAtUtcIso()).isEmpty();
    }

    @Test
    void getPlayedAtUtcIso_withPlayedAt_returnsIsoInstant() {
        MatchDto dto = new MatchDto();
        dto.setPlayedAt(LocalDateTime.of(2026, 8, 1, 18, 0));

        assertThat(dto.getPlayedAtUtcIso()).isNotEmpty().endsWith("Z");
    }
}
