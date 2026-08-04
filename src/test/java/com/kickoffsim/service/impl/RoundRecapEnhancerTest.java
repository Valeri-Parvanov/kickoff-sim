package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RoundRecapPromptData;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.RoundRecapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoundRecapEnhancerTest {

    @Mock
    private RoundRecapRepository roundRecapRepository;

    @Mock
    private OllamaRecapWriter ollamaRecapWriter;

    @InjectMocks
    private RoundRecapEnhancer enhancer;

    private final UUID recapId = UUID.randomUUID();

    @Test
    void enhance_missingRecap_doesNothing() {
        when(roundRecapRepository.findById(recapId)).thenReturn(Optional.empty());

        enhancer.enhance(recapId, promptData());

        verify(roundRecapRepository, never()).save(any());
    }

    @Test
    void enhance_restyleChangesContent_savesTheUpgradedRecap() {
        RoundRecap recap = recap("BIG_WIN|50|Base|Base body");
        when(roundRecapRepository.findById(recapId)).thenReturn(Optional.of(recap));
        when(ollamaRecapWriter.restyle(eq("BIG_WIN|50|Base|Base body"), any()))
                .thenReturn("BIG_WIN|50|Fresh|Fresh prose");

        enhancer.enhance(recapId, promptData());

        assertThat(recap.getContent()).isEqualTo("BIG_WIN|50|Fresh|Fresh prose");
        verify(roundRecapRepository).save(recap);
    }

    @Test
    void enhance_restyleUnchanged_doesNotSave() {
        RoundRecap recap = recap("BIG_WIN|50|Base|Base body");
        when(roundRecapRepository.findById(recapId)).thenReturn(Optional.of(recap));
        when(ollamaRecapWriter.restyle(any(), any())).thenReturn("BIG_WIN|50|Base|Base body");

        enhancer.enhance(recapId, promptData());

        verify(roundRecapRepository, never()).save(any());
    }

    @Test
    void enhance_restyleFails_isSwallowed() {
        RoundRecap recap = recap("BIG_WIN|50|Base|Base body");
        when(roundRecapRepository.findById(recapId)).thenReturn(Optional.of(recap));
        when(ollamaRecapWriter.restyle(any(), any())).thenThrow(new RuntimeException("ollama down"));

        enhancer.enhance(recapId, promptData());

        verify(roundRecapRepository, never()).save(any());
    }

    private RoundRecap recap(String content) {
        RoundRecap recap = new RoundRecap();
        recap.setId(recapId);
        recap.setContent(content);
        return recap;
    }

    private RoundRecapPromptData promptData() {
        return new RoundRecapPromptData("L", 1, "en", "English",
                List.of(), List.of(), 6, List.of(), List.of(), null);
    }
}
