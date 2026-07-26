package com.kickoffsim.dto;

import java.time.LocalDateTime;

public record RoundRecapView(
        String content,
        LocalDateTime generatedAt,
        String localeTag,
        String sourceFingerprint) {
}
