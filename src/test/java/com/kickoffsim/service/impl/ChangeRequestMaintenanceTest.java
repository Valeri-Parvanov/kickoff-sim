package com.kickoffsim.service.impl;

import com.kickoffsim.model.ChangeRequest;
import com.kickoffsim.model.ChangeRequestStatus;
import com.kickoffsim.repository.ChangeRequestRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestMaintenanceTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private UserService userService;
    @Mock private LeagueService leagueService;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private MatchService matchService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Validator validator;

    @InjectMocks
    private ChangeRequestServiceImpl service;

    @Test
    void expireStalePending_marksOldPendingAsRejected() {
        ChangeRequest first = new ChangeRequest();
        first.setStatus(ChangeRequestStatus.PENDING);
        ChangeRequest second = new ChangeRequest();
        second.setStatus(ChangeRequestStatus.PENDING);

        when(changeRequestRepository.findAllByStatusAndRequestedAtBefore(eq(ChangeRequestStatus.PENDING), any()))
                .thenReturn(List.of(first, second));

        int count = service.expireStalePending(14);

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
        assertThat(second.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
        assertThat(first.getRejectionReason()).contains("expired");
        verify(changeRequestRepository).saveAll(anyList());
    }

    @Test
    void purgeResolvedOlderThan_returnsRepositoryCount() {
        when(changeRequestRepository.deleteByStatusInAndReviewedAtBefore(anyCollection(), any()))
                .thenReturn(5L);

        long removed = service.purgeResolvedOlderThan(30);

        assertThat(removed).isEqualTo(5L);
    }
}
