package com.kickoffsim.client;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class NotificationDto {

    private UUID id;
    private UUID matchId;
    private String message;
    private String type;
    private boolean read;
    private LocalDateTime createdAt;
}
