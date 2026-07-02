package bg.softuni.footballleague.service;

import bg.softuni.footballleague.dto.ChangeRequestView;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

public interface ChangeRequestService {

    boolean submitOrExecute(EntityType entityType, ChangeAction action, Object dto, UUID targetId,
                             Authentication authentication);

    List<ChangeRequestView> findPending();

    long countPending();

    List<ChangeRequestView> findMine(Authentication authentication);

    Object getPayloadForResubmit(UUID id, Authentication authentication);

    void approve(UUID id, Authentication authentication);

    void reject(UUID id, Authentication authentication, String reason);

    int expireStalePending(int olderThanDays);

    long purgeResolvedOlderThan(int olderThanDays);
}
