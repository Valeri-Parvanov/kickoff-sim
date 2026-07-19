package com.kickoffsim.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotifyRequest {

    private UUID userId;
    private UUID matchId;
    private String message;
    private String type;
}
