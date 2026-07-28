package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.NotifyRequest;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.security.SecurityConfig;
import com.kickoffsim.service.UserService;
import com.kickoffsim.web.LiveMatchJsSupport;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.web.MatchStatusSupport;
import com.kickoffsim.web.SseEmitterRegistry;
import com.kickoffsim.web.StandingsSupport;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationClient notificationClient;
    private final UserService userService;
    private final TeamService teamService;
    private final LeagueService leagueService;
    private final MatchService matchService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public record SubscriptionView(
            UUID subscriptionId,
            String entityType,
            UUID entityId,
            String entityName,
            String leagueName,
            UUID leagueId,
            Integer standingPosition,
            long remainingMatches,
            int teamCount
    ) {}

    @GetMapping("/feed")
    public String feedPage(Authentication authentication, Model model) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            List<SubscriptionDto> subs = notificationClient.getSubscriptions(userId);

            List<SubscriptionView> teamViews = new ArrayList<>();
            List<SubscriptionView> leagueViews = new ArrayList<>();

            Set<UUID> staleSubscriptionIds = new LinkedHashSet<>();
            Set<UUID> followedTeamIds = entityIdsOfType(subs, "TEAM");
            Map<UUID, TeamDto> teamsById = teamService.findAllByIds(followedTeamIds).stream()
                    .collect(Collectors.toMap(TeamDto::getId, team -> team));
            Map<UUID, LeagueDetailView> leagueDetailsById = new HashMap<>();

            for (SubscriptionDto s : subs) {
                SubscriptionView v;
                try {
                    v = buildView(s, teamsById, leagueDetailsById);
                } catch (EntityNotFoundException e) {
                    staleSubscriptionIds.add(s.getId());
                    continue;
                } catch (Exception e) {
                    log.warn("Could not enrich subscription {}: {}", s.getId(), e.getMessage());
                    continue;
                }
                if (v == null) {
                    continue;
                }
                if ("TEAM".equals(s.getEntityType())) {
                    teamViews.add(v);
                } else {
                    leagueViews.add(v);
                }
            }

            dropStaleSubscriptions(staleSubscriptionIds);
            if (!staleSubscriptionIds.isEmpty()) {
                subs = subs.stream()
                        .filter(s -> !staleSubscriptionIds.contains(s.getId()))
                        .toList();
            }

            FollowedIds followed = followedIds(subs);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusDays(14);
            LocalDateTime liveThreshold = now.minusMinutes(90);

            List<MatchDto> pastWindow = matchService.findFollowedInWindow(
                    cutoff, now, followed.teamIds(), followed.matchIds(), true);
            List<MatchDto> futureWindow = matchService.findFollowedInWindow(
                    now, FAR_FUTURE, followed.teamIds(), followed.matchIds(), false);

            List<MatchDto> live = liveMatches(pastWindow, followed, now, liveThreshold);

            List<MatchDto> upcoming = futureWindow.stream()
                    .filter(m -> m.getPlayedAt().isAfter(now))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                    .toList();

            List<MatchDto> recent = pastWindow.stream()
                    .filter(m -> !m.getPlayedAt().isAfter(liveThreshold) && m.getPlayedAt().isAfter(cutoff))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt).reversed())
                    .toList();

            List<Map<String, Object>> liveMatchesForJs = LiveMatchJsSupport.toJs(live, now);
            Map<UUID, Long> elapsedByMatchId = LiveMatchJsSupport.elapsedByMatchId(live, now);

            model.addAttribute("teamViews", teamViews);
            model.addAttribute("leagueViews", leagueViews);
            model.addAttribute("liveMatches", live);
            model.addAttribute("upcomingMatches", upcoming);
            model.addAttribute("recentMatches", recent);
            model.addAttribute("liveMatchesForJs", liveMatchesForJs);
            model.addAttribute("elapsedByMatchId", elapsedByMatchId);
            model.addAttribute("now", now);
            model.addAttribute("liveThreshold", liveThreshold);
            model.addAttribute("currentUrl", "/feed");
            model.addAttribute("subscribedMatchIds", followed.matchIds());
        } catch (Exception e) {
            log.warn("Could not load feed: {}", e.getMessage());
            model.addAttribute("teamViews", List.of());
            model.addAttribute("leagueViews", List.of());
            model.addAttribute("liveMatches", List.of());
            model.addAttribute("upcomingMatches", List.of());
            model.addAttribute("recentMatches", List.of());
            model.addAttribute("liveMatchesForJs", List.of());
            model.addAttribute("elapsedByMatchId", Map.of());
            model.addAttribute("now", LocalDateTime.now());
            model.addAttribute("liveThreshold", LocalDateTime.now().minusMinutes(90));
            model.addAttribute("currentUrl", "/feed");
            model.addAttribute("subscribedMatchIds", Set.of());
            model.addAttribute("warnMessage", "flash.notif.unavailable");
        }
        return "feed";
    }

    @GetMapping("/feed/live-summary")
    @ResponseBody
    public Map<String, Object> feedLiveSummary(Authentication authentication) {
        if (authentication == null) return Map.of("matches", List.of());
        try {
            UUID userId = userService.findByUsername(authentication.getName()).getId();
            List<SubscriptionDto> subs = notificationClient.getSubscriptions(userId);

            FollowedIds followed = followedIds(subs);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime liveThreshold = now.minusMinutes(90);

            List<MatchDto> live = liveMatches(
                    matchService.findInWindow(liveThreshold, now, true), followed, now, liveThreshold);

            List<Map<String, Object>> matches = live.stream()
                    .map(m -> {
                        Map<String, Object> entry = new LinkedHashMap<>(LiveMatchJsSupport.toJsEntry(m, now));
                        entry.put("homeTeamName", m.getHomeTeamName());
                        entry.put("homeTeamCity", m.getHomeTeamCity());
                        entry.put("awayTeamName", m.getAwayTeamName());
                        entry.put("awayTeamCity", m.getAwayTeamCity());
                        entry.put("leagueId", m.getLeagueId() != null ? m.getLeagueId().toString() : null);
                        entry.put("leagueName", m.getLeagueName());
                        entry.put("roundNumber", m.getRoundNumber());
                        entry.put("playedAtUtcIso", m.getPlayedAtUtcIso());
                        entry.put("followed", followed.matchIds().contains(m.getId()));
                        return entry;
                    })
                    .toList();

            return Map.of("matches", matches);
        } catch (Exception e) {
            return Map.of("matches", List.of());
        }
    }

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59);

    private static final Set<String> TOASTABLE_TYPES =
            Set.of("GOAL", "MATCH_KICKOFF", "MATCH_HALFTIME", "MATCH_SECONDHALF", "MATCH_FULLTIME");

    @GetMapping("/notifications/toasts")
    @ResponseBody
    public List<Map<String, Object>> liveToasts(Authentication authentication, HttpSession session) {
        if (authentication == null) return List.of();
        try {
            UUID userId = userService.findByUsername(authentication.getName()).getId();
            Object loginAt = session.getAttribute(SecurityConfig.LOGIN_AT_SESSION_ATTR);
            LocalDateTime cutoff = loginAt instanceof LocalDateTime loginAtTime
                    ? loginAtTime
                    : Instant.ofEpochMilli(session.getCreationTime())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
            return notificationClient.getNotifications(userId).stream()
                    .filter(n -> TOASTABLE_TYPES.contains(n.getType()))
                    .filter(n -> n.getCreatedAt() != null && n.getCreatedAt().isAfter(cutoff))
                    .map(n -> Map.<String, Object>of(
                            "id", n.getId().toString(),
                            "message", n.getMessage(),
                            "type", n.getType()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream(Authentication authentication) {
        if (authentication == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        return sseEmitterRegistry.register(userId);
    }

    @PostMapping("/notifications/subscribe")
    public String subscribe(
            @RequestParam UUID entityId,
            @RequestParam String entityType,
            @RequestParam(required = false) String returnUrl,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            notificationClient.subscribe(new SubscriptionRequest(userId, entityType, entityId));
            redirectAttributes.addFlashAttribute("statusMessage", "You are now following this " + entityType.toLowerCase() + ".");
            log.info("User {} subscribed to {} {}", authentication.getName(), entityType, entityId);
            backfillMatchSubscriptions(userId, entityType, entityId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warnMessage", "flash.notif.alreadyfollowing");
        }
        return returnUrl != null ? "redirect:" + returnUrl : "redirect:/feed";
    }

    private void backfillMatchSubscriptions(UUID userId, String entityType, UUID entityId) {
        try {
            List<MatchDto> matches;
            if ("TEAM".equals(entityType)) {
                TeamDto team = teamService.findById(entityId);
                if (team.getLeagueId() == null) return;
                matches = leagueService.findDetail(team.getLeagueId()).getMatches().stream()
                        .filter(m -> entityId.equals(m.getHomeTeamId()) || entityId.equals(m.getAwayTeamId()))
                        .toList();
            } else if ("LEAGUE".equals(entityType)) {
                matches = leagueService.findDetail(entityId).getMatches();
            } else {
                return;
            }

            Set<UUID> alreadyFollowed = notificationClient.getSubscriptions(userId).stream()
                    .filter(s -> "MATCH".equals(s.getEntityType()))
                    .map(SubscriptionDto::getEntityId)
                    .collect(Collectors.toSet());

            for (MatchDto match : matches) {
                if (alreadyFollowed.contains(match.getId())) continue;
                try {
                    notificationClient.subscribe(new SubscriptionRequest(userId, "MATCH", match.getId()));
                } catch (Exception e) {
                    log.warn("Could not backfill match subscription {} for user {}: {}", match.getId(), userId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not backfill match subscriptions for {} {} (user {}): {}", entityType, entityId, userId, e.getMessage());
        }
    }

    @PostMapping("/notifications/match/{matchId}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleMatchFollow(@PathVariable UUID matchId, Authentication authentication) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            SubscriptionDto existing = notificationClient.getSubscriptions(userId).stream()
                    .filter(s -> "MATCH".equals(s.getEntityType()) && matchId.equals(s.getEntityId()))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                notificationClient.unsubscribe(existing.getId());
                return ResponseEntity.ok(Map.of("following", false));
            }

            notificationClient.subscribe(new SubscriptionRequest(userId, "MATCH", matchId));
            sendMatchStatus(userId, matchId);
            return ResponseEntity.ok(Map.of("following", true));
        } catch (Exception ex) {
            log.warn("Match follow toggle failed for match {}: {}", matchId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Notification service is temporarily unavailable."));
        }
    }

    @DeleteMapping("/notifications/subscriptions/{id}")
    public String unsubscribe(
            @PathVariable UUID id,
            @RequestParam(required = false) String returnUrl,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            notificationClient.unsubscribe(id);
            redirectAttributes.addFlashAttribute("statusMessage", "flash.notif.unfollowed");
            log.info("User {} unsubscribed subscription {}", authentication.getName(), id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warnMessage", "flash.notif.unfollowfailed");
        }
        return returnUrl != null ? "redirect:" + returnUrl : "redirect:/feed";
    }

    private void sendMatchStatus(UUID userId, UUID matchId) {
        try {
            MatchDto match = matchService.findById(matchId);
            String home = match.getHomeTeamName();
            String away = match.getAwayTeamName();
            LocalDateTime now = LocalDateTime.now();
            String message;
            String type;
            if (match.getPlayedAt().isAfter(now)) {
                message = "Upcoming: " + home + " vs " + away + " — kicks off "
                        + match.getPlayedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
                type = "UPCOMING_MATCH";
            } else {
                message = MatchStatusSupport.liveStatusMessage(match, now);
                type = "MATCH_UPDATE";
            }
            notificationClient.notifyUser(new NotifyRequest(userId, matchId, message, type));
        } catch (Exception e) {
            log.warn("Could not send instant status for match {}: {}", matchId, e.getMessage());
        }
    }

    private SubscriptionView buildView(
            SubscriptionDto s,
            Map<UUID, TeamDto> teamsById,
            Map<UUID, LeagueDetailView> leagueDetailsById) {
        if ("TEAM".equals(s.getEntityType())) {
            TeamDto team = teamsById.get(s.getEntityId());
            if (team == null) {
                throw new EntityNotFoundException("Team not found");
            }
            String name = team.getName() + (team.getCity() != null ? " (" + team.getCity() + ")" : "");
            if (team.getLeagueId() == null) {
                return new SubscriptionView(s.getId(), "TEAM", s.getEntityId(), name, null, null, null, 0, 0);
            }
            LeagueDetailView league = leagueDetailsById.computeIfAbsent(
                    team.getLeagueId(), leagueService::findDetail);
            Integer position = StandingsSupport.positionOf(league.getStandings(), s.getEntityId());
            LocalDateTime now = LocalDateTime.now();
            long remaining = league.getMatches().stream()
                    .filter(m -> m.getPlayedAt().isAfter(now))
                    .filter(m -> s.getEntityId().equals(m.getHomeTeamId()) || s.getEntityId().equals(m.getAwayTeamId()))
                    .count();
            return new SubscriptionView(s.getId(), "TEAM", s.getEntityId(), name,
                    league.getName(), team.getLeagueId(), position, remaining, 0);
        }
        if ("LEAGUE".equals(s.getEntityType())) {
            LeagueDto league = leagueService.findById(s.getEntityId());
            long remaining = league.getTotalMatches() - league.getPlayedMatches();
            return new SubscriptionView(s.getId(), "LEAGUE", s.getEntityId(), league.getName(),
                    null, null, null, remaining, league.getTeamCount());
        }
        return null;
    }

    private record FollowedIds(Set<UUID> teamIds, Set<UUID> matchIds) {
    }

    private FollowedIds followedIds(List<SubscriptionDto> subs) {
        return new FollowedIds(entityIdsOfType(subs, "TEAM"), entityIdsOfType(subs, "MATCH"));
    }

    private Set<UUID> entityIdsOfType(List<SubscriptionDto> subs, String entityType) {
        return subs.stream()
                .filter(s -> entityType.equals(s.getEntityType()))
                .map(SubscriptionDto::getEntityId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isFollowed(MatchDto match, FollowedIds followed) {
        return followed.teamIds().contains(match.getHomeTeamId())
                || followed.teamIds().contains(match.getAwayTeamId())
                || followed.matchIds().contains(match.getId());
    }

    private List<MatchDto> liveMatches(Collection<MatchDto> matches, FollowedIds followed,
                                       LocalDateTime now, LocalDateTime liveThreshold) {
        return matches.stream()
                .filter(m -> !m.getPlayedAt().isAfter(now) && m.getPlayedAt().isAfter(liveThreshold))
                .filter(m -> isFollowed(m, followed))
                .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                .toList();
    }

    private void dropStaleSubscriptions(Set<UUID> staleSubscriptionIds) {
        for (UUID subscriptionId : staleSubscriptionIds) {
            try {
                notificationClient.unsubscribe(subscriptionId);
                log.info("Removed subscription {} because the followed entity no longer exists", subscriptionId);
            } catch (Exception e) {
                log.warn("Could not remove stale subscription {}: {}", subscriptionId, e.getMessage());
            }
        }
    }
}
