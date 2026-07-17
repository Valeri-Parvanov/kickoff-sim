package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.exception.ChangeRequestApprovalException;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.ChangeRequest;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.repository.ChangeRequestRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.MatchService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.ScheduleService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestServiceImplTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserService userService;
    @Mock private LeagueService leagueService;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private MatchService matchService;
    @Mock private ScheduleService scheduleService;
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

    @Test
    void submitOrExecute_adminUser_appliesDirectlyAndReturnsTrue() {
        TeamDto dto = new TeamDto();
        boolean result = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.CREATE, dto, null, authentication);

        assertThat(result).isTrue();
        verify(teamService).create(dto);
    }

    @Test
    void submitOrExecute_regularUser_savesChangeRequestAndReturnsFalse() throws Exception {
        User regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setUsername("user");
        regularUser.setRole(Role.USER);
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        TeamDto dto = new TeamDto();
        boolean result = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.CREATE, dto, null, authentication);

        assertThat(result).isFalse();
        verify(changeRequestRepository).save(any(ChangeRequest.class));
    }

    @Test
    void countPending_delegatesToRepository() {
        when(changeRequestRepository.countByStatus(ChangeRequestStatus.PENDING)).thenReturn(7L);

        assertThat(changeRequestService.countPending()).isEqualTo(7L);
    }

    @Test
    void countMyPending_delegatesToRepository() {
        when(changeRequestRepository.countByStatusAndRequestedBy(ChangeRequestStatus.PENDING, admin)).thenReturn(3L);

        assertThat(changeRequestService.countMyPending(authentication)).isEqualTo(3L);
    }

    @Test
    void cancelMine_ownPendingRequest_deletesIt() {
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.cancelMine(pending.getId(), authentication);

        verify(changeRequestRepository).deleteById(pending.getId());
    }

    @Test
    void cancelMine_notOwner_throwsChangeRequestApprovalException() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        otherUser.setRole(Role.USER);
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING);
        pending.setRequestedBy(otherUser);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> changeRequestService.cancelMine(pending.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("own requests");
    }

    @Test
    void cancelMine_notPending_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(approvedRequest.getId())).thenReturn(Optional.of(approvedRequest));

        assertThatThrownBy(() -> changeRequestService.cancelMine(approvedRequest.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void cancelMine_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.cancelMine(unknownId, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void cancelIfPending_ownPendingRequest_deletesIt() {
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.cancelIfPending(pending.getId(), authentication);

        verify(changeRequestRepository).deleteById(pending.getId());
    }

    @Test
    void cancelIfPending_notOwner_doesNotDelete() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING);
        pending.setRequestedBy(otherUser);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.cancelIfPending(pending.getId(), authentication);

        verify(changeRequestRepository, never()).deleteById(any());
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
