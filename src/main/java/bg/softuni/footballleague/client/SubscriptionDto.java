package bg.softuni.footballleague.client;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SubscriptionDto {

    private UUID id;
    private UUID userId;
    private String entityType;
    private UUID entityId;
    private LocalDateTime createdAt;
}
