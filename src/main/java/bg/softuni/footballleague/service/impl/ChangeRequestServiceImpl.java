package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.ChangeRequestView;
import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.exception.ChangeRequestApprovalException;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.ChangeRequest;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.repository.ChangeRequestRepository;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.MatchService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChangeRequestServiceImpl implements ChangeRequestService {

    private final ChangeRequestRepository changeRequestRepository;
    private final UserService userService;
    private final LeagueService leagueService;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    public boolean submitOrExecute(EntityType entityType, ChangeAction action, Object dto, UUID targetId,
                                    Authentication authentication) {
        User requester = userService.findByUsername(authentication.getName());

        if (requester.getRole() == Role.ADMIN) {
            applyChange(entityType, action, dto, targetId);
            return true;
        }

        Object payloadDto = action == ChangeAction.DELETE ? findCurrentDto(entityType, targetId) : dto;
        if (action != ChangeAction.DELETE) {
            enrichForDisplay(entityType, payloadDto);
        }

        ChangeRequest changeRequest = new ChangeRequest();
        changeRequest.setEntityType(entityType);
        changeRequest.setAction(action);
        changeRequest.setTargetId(targetId);
        changeRequest.setPayload(toJson(payloadDto));
        changeRequest.setStatus(ChangeRequestStatus.PENDING);
        changeRequest.setRequestedBy(requester);
        changeRequest.setRequestedAt(LocalDateTime.now());
        changeRequestRepository.save(changeRequest);
        return false;
    }

    @Override
    public List<ChangeRequestView> findPending() {
        return changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public List<ChangeRequestView> findMine(Authentication authentication) {
        User requester = userService.findByUsername(authentication.getName());
        return changeRequestRepository.findAllByRequestedByOrderByRequestedAtDesc(requester).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public Object getPayloadForResubmit(UUID id, Authentication authentication) {
        ChangeRequest changeRequest = getOrThrow(id);
        User requester = userService.findByUsername(authentication.getName());

        boolean ownedByRequester = changeRequest.getRequestedBy().getId().equals(requester.getId());
        boolean eligible = changeRequest.getStatus() == ChangeRequestStatus.REJECTED
                && changeRequest.getAction() != ChangeAction.DELETE;
        if (!ownedByRequester || !eligible) {
            throw new EntityNotFoundException("Change request with id %s not found".formatted(id));
        }

        return fromJson(changeRequest.getEntityType(), changeRequest.getPayload());
    }

    @Override
    public void approve(UUID id, Authentication authentication) {
        ChangeRequest changeRequest = getOrThrow(id);
        if (changeRequest.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ChangeRequestApprovalException(
                    "Change request is already " + changeRequest.getStatus().name().toLowerCase() + ".");
        }
        User reviewer = userService.findByUsername(authentication.getName());

        Object dto = changeRequest.getAction() == ChangeAction.DELETE
                ? null
                : fromJson(changeRequest.getEntityType(), changeRequest.getPayload());

        if (dto != null) {
            Set<ConstraintViolation<Object>> violations = validator.validate(dto);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));
                throw new ChangeRequestApprovalException(message);
            }
        }

        try {
            applyChange(changeRequest.getEntityType(), changeRequest.getAction(), dto, changeRequest.getTargetId());
        } catch (ChangeRequestApprovalException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ChangeRequestApprovalException(e.getMessage());
        }

        changeRequest.setStatus(ChangeRequestStatus.APPROVED);
        changeRequest.setReviewedBy(reviewer);
        changeRequest.setReviewedAt(LocalDateTime.now());
        changeRequestRepository.save(changeRequest);
    }

    @Override
    public void reject(UUID id, Authentication authentication, String reason) {
        ChangeRequest changeRequest = getOrThrow(id);
        if (changeRequest.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ChangeRequestApprovalException(
                    "Change request is already " + changeRequest.getStatus().name().toLowerCase() + ".");
        }
        User reviewer = userService.findByUsername(authentication.getName());

        changeRequest.setStatus(ChangeRequestStatus.REJECTED);
        changeRequest.setReviewedBy(reviewer);
        changeRequest.setReviewedAt(LocalDateTime.now());
        changeRequest.setRejectionReason(reason);
        changeRequestRepository.save(changeRequest);
    }

    private void applyChange(EntityType entityType, ChangeAction action, Object dto, UUID targetId) {
        switch (entityType) {
            case LEAGUE -> applyLeagueChange(action, (LeagueDto) dto, targetId);
            case TEAM -> applyTeamChange(action, (TeamDto) dto, targetId);
            case PLAYER -> applyPlayerChange(action, (PlayerDto) dto, targetId);
            case MATCH -> applyMatchChange(action, (MatchDto) dto, targetId);
        }
    }

    private void applyLeagueChange(ChangeAction action, LeagueDto dto, UUID targetId) {
        switch (action) {
            case CREATE -> leagueService.create(dto);
            case UPDATE -> leagueService.update(targetId, dto);
            case DELETE -> leagueService.delete(targetId);
        }
    }

    private void applyTeamChange(ChangeAction action, TeamDto dto, UUID targetId) {
        switch (action) {
            case CREATE -> teamService.create(dto);
            case UPDATE -> teamService.update(targetId, dto);
            case DELETE -> teamService.delete(targetId);
        }
    }

    private void applyPlayerChange(ChangeAction action, PlayerDto dto, UUID targetId) {
        switch (action) {
            case CREATE -> playerService.create(dto);
            case UPDATE -> playerService.update(targetId, dto);
            case DELETE -> playerService.delete(targetId);
        }
    }

    private void applyMatchChange(ChangeAction action, MatchDto dto, UUID targetId) {
        switch (action) {
            case CREATE -> matchService.create(dto);
            case UPDATE -> matchService.update(targetId, dto);
            case DELETE -> matchService.delete(targetId);
        }
    }

    private Object findCurrentDto(EntityType entityType, UUID targetId) {
        return switch (entityType) {
            case LEAGUE -> leagueService.findById(targetId);
            case TEAM -> teamService.findById(targetId);
            case PLAYER -> playerService.findById(targetId);
            case MATCH -> matchService.findById(targetId);
        };
    }

    private void enrichForDisplay(EntityType entityType, Object dto) {
        switch (entityType) {
            case TEAM -> {
                TeamDto d = (TeamDto) dto;
                if (d.getLeagueId() != null) {
                    d.setLeagueName(leagueService.findById(d.getLeagueId()).getName());
                }
            }
            case PLAYER -> {
                PlayerDto d = (PlayerDto) dto;
                if (d.getTeamId() != null) {
                    d.setTeamName(teamService.findById(d.getTeamId()).getName());
                }
            }
            case MATCH -> {
                MatchDto d = (MatchDto) dto;
                if (d.getHomeTeamId() != null) {
                    d.setHomeTeamName(teamService.findById(d.getHomeTeamId()).getName());
                }
                if (d.getAwayTeamId() != null) {
                    d.setAwayTeamName(teamService.findById(d.getAwayTeamId()).getName());
                }
            }
            case LEAGUE -> {
            }
        }
    }

    private String toJson(Object dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new ChangeRequestApprovalException("Failed to serialize change request payload");
        }
    }

    private Object fromJson(EntityType entityType, String payload) {
        try {
            return switch (entityType) {
                case LEAGUE -> objectMapper.readValue(payload, LeagueDto.class);
                case TEAM -> objectMapper.readValue(payload, TeamDto.class);
                case PLAYER -> objectMapper.readValue(payload, PlayerDto.class);
                case MATCH -> objectMapper.readValue(payload, MatchDto.class);
            };
        } catch (Exception e) {
            throw new ChangeRequestApprovalException("Failed to read change request payload");
        }
    }

    private ChangeRequest getOrThrow(UUID id) {
        return changeRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Change request with id %s not found".formatted(id)));
    }

    private ChangeRequestView toView(ChangeRequest changeRequest) {
        ChangeRequestView view = new ChangeRequestView();
        view.setId(changeRequest.getId());
        view.setEntityType(changeRequest.getEntityType());
        view.setAction(changeRequest.getAction());
        view.setTargetId(changeRequest.getTargetId());
        view.setDetails(buildDetails(changeRequest));
        view.setStatus(changeRequest.getStatus());
        view.setRequestedByUsername(changeRequest.getRequestedBy().getUsername());
        view.setRequestedAt(changeRequest.getRequestedAt());
        view.setReviewedAt(changeRequest.getReviewedAt());
        view.setRejectionReason(changeRequest.getRejectionReason());
        return view;
    }

    private List<String> buildDetails(ChangeRequest changeRequest) {
        try {
            Object dto = fromJson(changeRequest.getEntityType(), changeRequest.getPayload());
            return switch (changeRequest.getEntityType()) {
                case LEAGUE -> {
                    LeagueDto d = (LeagueDto) dto;
                    yield List.of("Name: " + d.getName(), "Country: " + d.getCountry());
                }
                case TEAM -> {
                    TeamDto d = (TeamDto) dto;
                    yield List.of("Name: " + d.getName(), "City: " + d.getCity(), "League: " + d.getLeagueName());
                }
                case PLAYER -> {
                    PlayerDto d = (PlayerDto) dto;
                    yield List.of("Name: " + d.getFirstName() + " " + d.getLastName(),
                            "Shirt number: " + d.getShirtNumber(), "Team: " + d.getTeamName());
                }
                case MATCH -> {
                    MatchDto d = (MatchDto) dto;
                    yield List.of("Home team: " + d.getHomeTeamName(), "Away team: " + d.getAwayTeamName(),
                            "Played at: " + d.getPlayedAt());
                }
            };
        } catch (ChangeRequestApprovalException e) {
            return List.of("(unable to read change details)");
        }
    }
}
