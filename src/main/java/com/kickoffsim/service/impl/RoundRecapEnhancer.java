package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.RoundRecapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundRecapEnhancer {

    private final RoundRecapRepository roundRecapRepository;
    private final OllamaRecapWriter ollamaRecapWriter;

    @Async("recapExecutor")
    @Transactional
    public void enhance(UUID recapId, RoundRecapPromptData promptData) {
        Optional<RoundRecap> stored = roundRecapRepository.findById(recapId);
        if (stored.isEmpty()) {
            return;
        }
        RoundRecap recap = stored.get();
        try {
            String restyled = ollamaRecapWriter.restyle(recap.getContent(), promptData);
            if (!restyled.equals(recap.getContent())) {
                recap.setContent(restyled);
                recap.setGeneratedAt(LocalDateTime.now());
                roundRecapRepository.save(recap);
            }
        } catch (RuntimeException exception) {
            log.warn("Background recap enhancement failed for {}: {}", recapId, exception.getMessage());
        }
    }
}
