package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.NotifyRequest;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.UserService;
import com.kickoffsim.web.LiveMatchJsSupport;
import com.kickoffsim.web.MatchFollowSupport;
import com.kickoffsim.web.MatchStatusSupport;
import com.kickoffsim.web.StandingsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

@Controller
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationClient notificationClient;
    private final UserService userService;
    private final TeamService teamService;
    private final LeagueService leagueService;
    private final MatchService matchService;
    private final MatchFollowSupport matchFollowSupport;

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
            Set<UUID> leagueIds = new LinkedHashSet<>();

            for (SubscriptionDto s : subs) {
                SubscriptionView v = buildView(s);
                if ("TEAM".equals(s.getEntityType())) {
                    teamViews.add(v);
                    if (v.leagueId() != null) leagueIds.add(v.leagueId());
                } else if ("LEAGUE".equals(s.getEntityType())) {
                    leagueViews.add(v);
                    leagueIds.add(s.getEntityId());
                }
            }

            Set<UUID> followedTeamIds = subs.stream()
                    .filter(s -> "TEAM".equals(s.getEntityType()))
                    .map(SubscriptionDto::getEntityId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Set<UUID> followedMatchIds = subs.stream()
                    .filter(s -> "MATCH".equals(s.getEntityType()))
                    .map(SubscriptionDto::getEntityId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Map<UUID, MatchDto> matchMap = new LinkedHashMap<>();
            for (UUID leagueId : leagueIds) {
                try {
                    leagueService.findDetail(leagueId).getMatches()
                            .forEach(m -> matchMap.put(m.getId(), m));
                } catch (Exception ignored) {}
            }
            for (UUID matchId : followedMatchIds) {
                if (matchMap.containsKey(matchId)) continue;
                try {
                    MatchDto m = matchService.findById(matchId);
                    matchMap.put(m.getId(), m);
                } catch (Exception ignored) {}
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusDays(14);
            LocalDateTime liveThreshold = now.minusMinutes(90);

            List<MatchDto> live = matchMap.values().stream()
                    .filter(m -> !m.getPlayedAt().isAfter(now) && m.getPlayedAt().isAfter(liveThreshold))
                    .filter(m -> followedTeamIds.contains(m.getHomeTeamId())
                            || followedTeamIds.contains(m.getAwayTeamId())
                            || followedMatchIds.contains(m.getId()))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                    .toList();

            List<MatchDto> upcoming = matchMap.values().stream()
                    .filter(m -> m.getPlayedAt().isAfter(now))
                    .filter(m -> followedTeamIds.contains(m.getHomeTeamId())
                            || followedTeamIds.contains(m.getAwayTeamId())
                            || followedMatchIds.contains(m.getId()))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                    .toList();

            List<MatchDto> recent = matchMap.values().stream()
                    .filter(m -> !m.getPlayedAt().isAfter(liveThreshold) && m.getPlayedAt().isAfter(cutoff))
                    .filter(m -> followedTeamIds.contains(m.getHomeTeamId())
                            || followedTeamIds.contains(m.getAwayTeamId())
                            || followedMatchIds.contains(m.getId()))
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
            model.addAttribute("subscribedMatchIds", matchFollowSupport.subscribedMatchIds(authentication));
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
            model.addAttribute("warnMessage", "Notification service is temporarily unavailable.");
        }
        return "feed";
    }

    private static final Set<String> TOASTABLE_TYPES =
            Set.of("GOAL", "MATCH_HALFTIME", "MATCH_FULLTIME");

    @GetMapping("/notifications/toasts")
    @ResponseBody
    public List<Map<String, Object>> liveToasts(Authentication authentication) {
        if (authentication == null) return List.of();
        try {
            UUID userId = userService.findByUsername(authentication.getName()).getId();
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(3);
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
            redirectAttributes.addFlashAttribute("warnMessage", "Already following or notification service unavailable.");
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
            redirectAttributes.addFlashAttribute("statusMessage", "Unfollowed successfully.");
            log.info("User {} unsubscribed subscription {}", authentication.getName(), id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warnMessage", "Could not unfollow. Try again.");
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

    private SubscriptionView buildView(SubscriptionDto s) {
        try {
            if ("TEAM".equals(s.getEntityType())) {
                TeamDto team = teamService.findById(s.getEntityId());
                String name = team.getName() + (team.getCity() != null ? " (" + team.getCity() + ")" : "");
                if (team.getLeagueId() == null) {
                    return new SubscriptionView(s.getId(), "TEAM", s.getEntityId(), name, null, null, null, 0, 0);
                }
                LeagueDetailView league = leagueService.findDetail(team.getLeagueId());
                Integer position = StandingsSupport.positionOf(league.getStandings(), s.getEntityId());
                LocalDateTime now = LocalDateTime.now();
                long remaining = league.getMatches().stream()
                        .filter(m -> m.getPlayedAt().isAfter(now))
                        .filter(m -> s.getEntityId().equals(m.getHomeTeamId()) || s.getEntityId().equals(m.getAwayTeamId()))
                        .count();
                return new SubscriptionView(s.getId(), "TEAM", s.getEntityId(), name,
                        league.getName(), team.getLeagueId(), position, remaining, 0);
            } else if ("LEAGUE".equals(s.getEntityType())) {
                LeagueDetailView league = leagueService.findDetail(s.getEntityId());
                LocalDateTime now = LocalDateTime.now();
                long remaining = league.getMatches().stream()
                        .filter(m -> m.getPlayedAt().isAfter(now)).count();
                return new SubscriptionView(s.getId(), "LEAGUE", s.getEntityId(), league.getName(),
                        null, null, null, remaining, league.getTeams().size());
            }
        } catch (Exception e) {
            log.warn("Could not enrich subscription {}: {}", s.getId(), e.getMessage());
        }
        return new SubscriptionView(s.getId(), s.getEntityType(), s.getEntityId(),
                s.getEntityId().toString(), null, null, null, 0, 0);
    }
}
