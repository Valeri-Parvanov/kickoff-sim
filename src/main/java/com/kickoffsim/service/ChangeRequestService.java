package com.kickoffsim.service;

import com.kickoffsim.dto.ChangeRequestView;
import com.kickoffsim.model.ChangeAction;
import com.kickoffsim.model.EntityType;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

public interface ChangeRequestService {

    boolean submitOrExecute(EntityType entityType, ChangeAction action, Object dto, UUID targetId,
                             Authentication authentication);

    List<ChangeRequestView> findPending();

    long countPending();

    long countMyPending(Authentication authentication);

    List<ChangeRequestView> findMine(Authentication authentication);

    Object getPayloadForResubmit(UUID id, Authentication authentication);

    String getRejectionReason(UUID id, Authentication authentication);

    void cancelIfPending(UUID id, Authentication authentication);

    void cancelMine(UUID id, Authentication authentication);

    void approve(UUID id, Authentication authentication);

    void reject(UUID id, Authentication authentication, String reason);

    int expireStalePending(int olderThanDays);

    long purgeResolvedOlderThan(int olderThanDays);
}
