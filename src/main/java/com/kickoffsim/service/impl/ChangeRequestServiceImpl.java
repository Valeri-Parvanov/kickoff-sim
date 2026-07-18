package com.kickoffsim.service.impl;

import com.kickoffsim.dto.*;
import com.kickoffsim.exception.ChangeRequestApprovalException;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.model.*;
import com.kickoffsim.repository.ChangeRequestRepository;
import com.kickoffsim.repository.TeamRepository;
import com.kickoffsim.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ChangeRequestServiceImpl implements ChangeRequestService {

    private final ChangeRequestRepository changeRequestRepository;
    private final TeamRepository teamRepository;
    private final UserService userService;
    private final LeagueService leagueService;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final MatchService matchService;
    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    private final Map<EntityType, EntityChangeApplier> changeAppliers;
    private final Map<ChangeAction, ActionApplier<LeagueDto>> leagueChangeAppliers;
    private final Map<ChangeAction, ActionApplier<TeamDto>> teamChangeAppliers;
    private final Map<ChangeAction, ActionApplier<PlayerDto>> playerChangeAppliers;
    private final Map<ChangeAction, ActionApplier<MatchDto>> matchChangeAppliers;
    private final Map<EntityType, Consumer<Object>> displayEnrichers;
    private final Map<EntityType, Function<UUID, Object>> currentDtoFinders;

    public ChangeRequestServiceImpl(ChangeRequestRepository changeRequestRepository, TeamRepository teamRepository,
                                     UserService userService, LeagueService leagueService, TeamService teamService,
                                     PlayerService playerService, MatchService matchService,
                                     ScheduleService scheduleService, ObjectMapper objectMapper, Validator validator) {
        this.changeRequestRepository = changeRequestRepository;
        this.teamRepository = teamRepository;
        this.userService = userService;
        this.leagueService = leagueService;
        this.teamService = teamService;
        this.playerService = playerService;
        this.matchService = matchService;
        this.scheduleService = scheduleService;
        this.objectMapper = objectMapper;
        this.validator = validator;

        this.leagueChangeAppliers = Map.of(
                ChangeAction.CREATE, (dto, targetId) -> {
                    leagueService.create(dto);
                    UUID newId = leagueService.findAll().stream()
                            .filter(l -> dto.getName().equals(l.getName()))
                            .map(LeagueDto::getId)
                            .findFirst().orElse(null);
                    if (newId != null) {
                        LocalDate startDate = dto.getScheduleStartDate() != null
                                ? dto.getScheduleStartDate() : LocalDate.now();
                        LocalTime startTime = dto.getScheduleStartTime() != null
                                ? dto.getScheduleStartTime() : LocalTime.of(11, 0);
                        try {
                            scheduleService.generate(newId, startDate, startTime);
                        } catch (Exception e) {
                            log.warn("Schedule generation failed after league approval: {}", e.getMessage());
                        }
                    }
                },
                ChangeAction.UPDATE, (dto, targetId) -> leagueService.update(targetId, dto),
                ChangeAction.DELETE, (dto, targetId) -> leagueService.delete(targetId)
        );

        this.teamChangeAppliers = Map.of(
                ChangeAction.CREATE, (dto, targetId) -> teamService.create(dto),
                ChangeAction.UPDATE, (dto, targetId) -> teamService.update(targetId, dto),
                ChangeAction.DELETE, (dto, targetId) -> teamService.delete(targetId)
        );

        this.playerChangeAppliers = Map.of(
                ChangeAction.CREATE, (dto, targetId) -> playerService.create(dto),
                ChangeAction.UPDATE, (dto, targetId) -> playerService.update(targetId, dto),
                ChangeAction.DELETE, (dto, targetId) -> playerService.delete(targetId)
        );

        this.matchChangeAppliers = Map.of(
                ChangeAction.CREATE, (dto, targetId) -> matchService.create(dto),
                ChangeAction.UPDATE, (dto, targetId) -> matchService.update(targetId, dto),
                ChangeAction.DELETE, (dto, targetId) -> matchService.delete(targetId)
        );

        this.changeAppliers = Map.of(
                EntityType.LEAGUE, (action, dto, targetId) -> applyLeagueChange(action, (LeagueDto) dto, targetId),
                EntityType.TEAM, (action, dto, targetId) -> applyTeamChange(action, (TeamDto) dto, targetId),
                EntityType.PLAYER, (action, dto, targetId) -> applyPlayerChange(action, (PlayerDto) dto, targetId),
                EntityType.MATCH, (action, dto, targetId) -> applyMatchChange(action, (MatchDto) dto, targetId),
                EntityType.TEAM_SQUAD, (action, dto, targetId) -> applyTeamSquadChange((TeamSquadPayload) dto)
        );

        this.displayEnrichers = Map.of(
                EntityType.TEAM, dto -> {
                    TeamDto d = (TeamDto) dto;
                    if (d.getLeagueId() != null) {
                        d.setLeagueName(leagueService.findById(d.getLeagueId()).getName());
                    }
                },
                EntityType.PLAYER, dto -> {
                    PlayerDto d = (PlayerDto) dto;
                    if (d.getTeamId() != null) {
                        d.setTeamName(teamService.findById(d.getTeamId()).getName());
                    }
                },
                EntityType.MATCH, dto -> {
                    MatchDto d = (MatchDto) dto;
                    if (d.getHomeTeamId() != null) {
                        d.setHomeTeamName(teamService.findById(d.getHomeTeamId()).getName());
                    }
                    if (d.getAwayTeamId() != null) {
                        d.setAwayTeamName(teamService.findById(d.getAwayTeamId()).getName());
                    }
                },
                EntityType.LEAGUE, dto -> { },
                EntityType.TEAM_SQUAD, dto -> {
                    TeamDto team = ((TeamSquadPayload) dto).getTeam();
                    if (team.getLeagueName() == null && team.getLeagueId() != null) {
                        team.setLeagueName(leagueService.findById(team.getLeagueId()).getName());
                    }
                }
        );

        this.currentDtoFinders = Map.of(
                EntityType.LEAGUE, leagueService::findById,
                EntityType.TEAM, teamService::findById,
                EntityType.PLAYER, playerService::findById,
                EntityType.MATCH, matchService::findById,
                EntityType.TEAM_SQUAD, targetId -> {
                    throw new ChangeRequestApprovalException("Squad requests cannot be deleted");
                }
        );
    }

    @Override
    @Transactional
    public boolean submitOrExecute(EntityType entityType, ChangeAction action, Object dto, UUID targetId,
                                    Authentication authentication) {
        User requester = userService.findByUsername(authentication.getName());

        if (requester.getRole() == Role.ADMIN) {
            applyChange(entityType, action, dto, targetId);
            log.info("Admin {} applied {} {} directly", requester.getUsername(), action, entityType);
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
        log.info("User {} submitted {} {} request for approval", requester.getUsername(), action, entityType);
        return false;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public List<ChangeRequestView> findPending() {
        return changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public long countPending() {
        return changeRequestRepository.countByStatus(ChangeRequestStatus.PENDING);
    }

    @Override
    public long countMyPending(Authentication authentication) {
        User requester = userService.findByUsername(authentication.getName());
        return changeRequestRepository.countByStatusAndRequestedBy(ChangeRequestStatus.PENDING, requester);
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public List<ChangeRequestView> findMine(Authentication authentication) {
        User requester = userService.findByUsername(authentication.getName());
        return changeRequestRepository.findAllByRequestedByOrderByRequestedAtDesc(requester).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public Object getPayloadForResubmit(UUID id, Authentication authentication) {
        ChangeRequest changeRequest = getOrThrow(id);
        User requester = userService.findByUsername(authentication.getName());

        boolean ownedByRequester = changeRequest.getRequestedBy().getId().equals(requester.getId());
        boolean eligible = changeRequest.getAction() != ChangeAction.DELETE
                && (changeRequest.getStatus() == ChangeRequestStatus.REJECTED
                    || changeRequest.getStatus() == ChangeRequestStatus.PENDING);
        if (!ownedByRequester || !eligible) {
            throw new EntityNotFoundException("Change request with id %s not found".formatted(id));
        }

        return fromJson(changeRequest.getEntityType(), changeRequest.getPayload());
    }

    @Override
    @Transactional
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
            changeRequest.setStatus(ChangeRequestStatus.APPROVED);
            changeRequest.setReviewedBy(reviewer);
            changeRequest.setReviewedAt(LocalDateTime.now());
            changeRequestRepository.save(changeRequest);
        } catch (ChangeRequestApprovalException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new ChangeRequestApprovalException(duplicateNameMessage(changeRequest.getEntityType(), dto));
        } catch (RuntimeException e) {
            throw new ChangeRequestApprovalException(e.getMessage());
        }
        log.info("Change request {} approved by {}", id, reviewer.getUsername());
    }

    @Override
    @Transactional
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
        log.info("Change request {} rejected by {}", id, reviewer.getUsername());
    }

    @Override
    @Transactional
    public int expireStalePending(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        List<ChangeRequest> stale =
                changeRequestRepository.findAllByStatusAndRequestedAtBefore(ChangeRequestStatus.PENDING, cutoff);
        for (ChangeRequest request : stale) {
            request.setStatus(ChangeRequestStatus.REJECTED);
            request.setReviewedAt(LocalDateTime.now());
            request.setRejectionReason("Automatically expired after " + olderThanDays + " days without review");
        }
        changeRequestRepository.saveAll(stale);
        if (!stale.isEmpty()) {
            log.info("Expired {} stale pending change request(s)", stale.size());
        }
        return stale.size();
    }

    @Override
    @Transactional
    public long purgeResolvedOlderThan(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        long removed = changeRequestRepository.deleteByStatusInAndReviewedAtBefore(
                List.of(ChangeRequestStatus.APPROVED, ChangeRequestStatus.REJECTED), cutoff);
        if (removed > 0) {
            log.info("Purged {} resolved change request(s) older than {} days", removed, olderThanDays);
        }
        return removed;
    }

    @FunctionalInterface
    private interface ActionApplier<T> {
        void apply(T dto, UUID targetId);
    }

    @FunctionalInterface
    private interface EntityChangeApplier {
        void apply(ChangeAction action, Object dto, UUID targetId);
    }

    private void applyChange(EntityType entityType, ChangeAction action, Object dto, UUID targetId) {
        changeAppliers.get(entityType).apply(action, dto, targetId);
    }

    private void applyTeamSquadChange(TeamSquadPayload payload) {
        UUID teamId = payload.getTeam().getId() != null
                ? payload.getTeam().getId()
                : teamService.create(payload.getTeam()).getId();
        for (PlayerDto player : payload.getPlayers()) {
            player.setTeamId(teamId);
            playerService.create(player);
        }
    }

    private void applyLeagueChange(ChangeAction action, LeagueDto dto, UUID targetId) {
        leagueChangeAppliers.get(action).apply(dto, targetId);
    }

    private void applyTeamChange(ChangeAction action, TeamDto dto, UUID targetId) {
        teamChangeAppliers.get(action).apply(dto, targetId);
    }

    private void applyPlayerChange(ChangeAction action, PlayerDto dto, UUID targetId) {
        playerChangeAppliers.get(action).apply(dto, targetId);
    }

    private void applyMatchChange(ChangeAction action, MatchDto dto, UUID targetId) {
        matchChangeAppliers.get(action).apply(dto, targetId);
    }

    private Object findCurrentDto(EntityType entityType, UUID targetId) {
        return currentDtoFinders.get(entityType).apply(targetId);
    }

    private void enrichForDisplay(EntityType entityType, Object dto) {
        displayEnrichers.get(entityType).accept(dto);
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
                case TEAM_SQUAD -> objectMapper.readValue(payload, TeamSquadPayload.class);
            };
        } catch (Exception e) {
            throw new ChangeRequestApprovalException("Failed to read change request payload");
        }
    }

    private String duplicateNameMessage(EntityType entityType, Object dto) {
        if (dto == null) {
            return "That name is already taken. Please choose a different one.";
        }
        return switch (entityType) {
            case TEAM -> "A team named '" + ((TeamDto) dto).getName() + "' already exists. Choose a different name.";
            case LEAGUE ->
                    "A league named '" + ((LeagueDto) dto).getName() + "' already exists. Choose a different name.";
            case TEAM_SQUAD -> "A team named '" + ((TeamSquadPayload) dto).getTeam().getName()
                    + "' already exists. Choose a different name.";
            default -> "That name is already taken. Please choose a different one.";
        };
    }

    @Override
    @Transactional
    public void cancelIfPending(UUID id, Authentication authentication) {
        changeRequestRepository.findById(id).ifPresent(cr -> {
            if (cr.getStatus() == ChangeRequestStatus.PENDING
                    && cr.getRequestedBy().getUsername().equals(authentication.getName())) {
                changeRequestRepository.deleteById(id);
                log.info("User {} cancelled PENDING change request {} on resubmit", authentication.getName(), id);
            }
        });
    }

    @Override
    @Transactional
    public void cancelMine(UUID id, Authentication authentication) {
        ChangeRequest cr = getOrThrow(id);
        User requester = userService.findByUsername(authentication.getName());
        if (!cr.getRequestedBy().getId().equals(requester.getId())) {
            throw new ChangeRequestApprovalException("You can only cancel your own requests.");
        }
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ChangeRequestApprovalException("Only pending requests can be cancelled.");
        }
        changeRequestRepository.deleteById(id);
        log.info("User {} cancelled change request {}", requester.getUsername(), id);
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
                    List<String> lines = new ArrayList<>();
                    lines.add("==League");
                    lines.add("Name: " + d.getName());
                    if (d.getScheduleStartDate() != null || d.getScheduleStartTime() != null) {
                        lines.add("==Schedule");
                        if (d.getScheduleStartDate() != null)
                            lines.add("Round 1 date: " + d.getScheduleStartDate());
                        if (d.getScheduleStartTime() != null)
                            lines.add("First kick-off: " + d.getScheduleStartTime());
                    }
                    if (d.getTeamIds() != null && !d.getTeamIds().isEmpty()) {
                        lines.add("==Teams (" + d.getTeamIds().size() + ")");
                        for (UUID teamId : d.getTeamIds()) {
                            teamRepository.findById(teamId).ifPresentOrElse(
                                t -> lines.add("· " + t.getName()
                                        + (t.getCity() != null ? " (" + t.getCity() + ")" : "")),
                                () -> lines.add("· [unknown team]")
                            );
                        }
                    }
                    yield lines;
                }
                case TEAM -> {
                    TeamDto d = (TeamDto) dto;
                    List<String> lines = new ArrayList<>();
                    lines.add("==Team");
                    lines.add("Name: " + d.getName());
                    if (d.getCity() != null && !d.getCity().isBlank())
                        lines.add("City: " + d.getCity());
                    if (d.getLeagueName() != null)
                        lines.add("League: " + d.getLeagueName());
                    yield lines;
                }
                case PLAYER -> {
                    PlayerDto d = (PlayerDto) dto;
                    List<String> lines = new ArrayList<>();
                    lines.add("==Player");
                    lines.add("Name: " + d.getFirstName() + " " + d.getLastName());
                    lines.add("Shirt #" + d.getShirtNumber());
                    if (d.getTeamName() != null)
                        lines.add("Team: " + d.getTeamName());
                    yield lines;
                }
                case MATCH -> {
                    MatchDto d = (MatchDto) dto;
                    List<String> lines = new ArrayList<>();
                    lines.add("==Match");
                    lines.add("Home: " + d.getHomeTeamName());
                    lines.add("Away: " + d.getAwayTeamName());
                    lines.add("Kick-off: " + d.getPlayedAt());
                    yield lines;
                }
                case TEAM_SQUAD -> {
                    TeamSquadPayload d = (TeamSquadPayload) dto;
                    List<String> lines = new ArrayList<>();
                    lines.add("==Team");
                    if (d.getTeam().getId() == null) {
                        String city = d.getTeam().getCity();
                        lines.add("Name: " + d.getTeam().getName()
                                + (city != null && !city.isBlank() ? " (" + city + ")" : ""));
                        if (d.getTeam().getLeagueName() != null)
                            lines.add("League: " + d.getTeam().getLeagueName());
                    } else {
                        lines.add("Add players to: " + d.getTeam().getName());
                    }
                    lines.add("==Players (" + d.getPlayers().size() + ")");
                    for (PlayerDto player : d.getPlayers()) {
                        lines.add("· #" + player.getShirtNumber() + " "
                                + player.getFirstName() + " " + player.getLastName());
                    }
                    yield lines;
                }
            };
        } catch (ChangeRequestApprovalException e) {
            return List.of("(unable to read change details)");
        }
    }
}
