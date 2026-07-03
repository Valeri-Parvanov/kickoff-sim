package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.exception.ChangeRequestApprovalException;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.ChangeRequest;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.repository.ChangeRequestRepository;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.MatchService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.service.UserService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestServiceImplTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private UserService userService;
    @Mock private LeagueService leagueService;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private MatchService matchService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Validator validator;
    @Mock private Authentication authentication;

    @InjectMocks
    private ChangeRequestServiceImpl changeRequestService;

    private User admin;
    private ChangeRequest approvedRequest;
    private ChangeRequest rejectedRequest;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);

        approvedRequest = changeRequest(ChangeRequestStatus.APPROVED);
        rejectedRequest = changeRequest(ChangeRequestStatus.REJECTED);

        when(authentication.getName()).thenReturn("admin");
        when(userService.findByUsername("admin")).thenReturn(admin);
    }

    @Test
    void approve_alreadyApproved_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(approvedRequest.getId()))
                .thenReturn(Optional.of(approvedRequest));

        assertThatThrownBy(() -> changeRequestService.approve(approvedRequest.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    void approve_alreadyRejected_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(rejectedRequest.getId()))
                .thenReturn(Optional.of(rejectedRequest));

        assertThatThrownBy(() -> changeRequestService.approve(rejectedRequest.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void reject_alreadyApproved_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(approvedRequest.getId()))
                .thenReturn(Optional.of(approvedRequest));

        assertThatThrownBy(() -> changeRequestService.reject(approvedRequest.getId(), authentication, "reason"))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    void reject_alreadyRejected_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(rejectedRequest.getId()))
                .thenReturn(Optional.of(rejectedRequest));

        assertThatThrownBy(() -> changeRequestService.reject(rejectedRequest.getId(), authentication, "reason"))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void approve_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.approve(unknownId, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void reject_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.reject(unknownId, authentication, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private ChangeRequest changeRequest(ChangeRequestStatus status) {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(UUID.randomUUID());
        cr.setEntityType(EntityType.LEAGUE);
        cr.setAction(ChangeAction.CREATE);
        cr.setStatus(status);
        cr.setRequestedBy(admin);
        cr.setRequestedAt(LocalDateTime.now().minusHours(1));
        cr.setPayload("{}");
        return cr;
    }
}
