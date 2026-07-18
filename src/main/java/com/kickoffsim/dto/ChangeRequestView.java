package bg.softuni.footballleague.dto;

import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import bg.softuni.footballleague.model.EntityType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ChangeRequestView {

    private UUID id;
    private EntityType entityType;
    private ChangeAction action;
    private UUID targetId;
    private List<String> details;
    private ChangeRequestStatus status;
    private String requestedByUsername;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
}
