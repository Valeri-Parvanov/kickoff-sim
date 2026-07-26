package com.kickoffsim.service;

import com.kickoffsim.dto.RoundRecapPromptData;

public interface RoundRecapAiClient {

    String generate(RoundRecapPromptData data);
}
