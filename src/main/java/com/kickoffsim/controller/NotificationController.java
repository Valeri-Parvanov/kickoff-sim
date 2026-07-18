package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.client.NotificationClient;
import bg.softuni.footballleague.client.NotificationDto;
import bg.softuni.footballleague.client.NotifyRequest;
import bg.softuni.footballleague.client.SubscriptionDto;
import bg.softuni.footballleague.client.SubscriptionRequest;
import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.LeagueDetailView;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.StandingRow;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.MatchService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.service.UserService;
import bg.softuni.footballleague.web.MatchFollowSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
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

    private static final int NOTIF_PAGE_SIZE = 10;

    public record NotificationView(
            UUID id,
            String message,
            String type,
            boolean read,
            LocalDateTime createdAt,
            UUID matchId,
            UUID homeTeamId,
            String homeTeamName,
            UUID awayTeamId,
            String awayTeamName,
            UUID leagueId,
            String leagueName
    ) {}

    @GetMapping("/notifications")
    public String notificationsPage(
            Authentication authentication,
            Model model,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            List<NotificationDto> all = notificationClient.getNotifications(userId).stream()
                    .sorted(Comparator.comparing(NotificationDto::getCreatedAt).reversed())
                    .toList();

            long countAll    = all.size();
            long countUnread = all.stream().filter(n -> !n.isRead()).count();
            long countRead   = all.stream().filter(NotificationDto::isRead).count();

            List<NotificationDto> filtered = switch (status) {
                case "unread" -> all.stream().filter(n -> !n.isRead()).toList();
                case "read"   -> all.stream().filter(NotificationDto::isRead).toList();
                default       -> all;
            };

            int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) NOTIF_PAGE_SIZE));
            int currentPage = Math.min(Math.max(page, 0), totalPages - 1);

            List<NotificationDto> shown = filtered.stream()
                    .skip((long) currentPage * NOTIF_PAGE_SIZE)
                    .limit(NOTIF_PAGE_SIZE)
                    .toList();

            model.addAttribute("notifications", enrich(shown));
            model.addAttribute("status", status);
            model.addAttribute("countAll", countAll);
            model.addAttribute("countUnread", countUnread);
            model.addAttribute("countRead", countRead);
            model.addAttribute("currentPage", currentPage);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("filteredCount", filtered.size());
        } catch (Exception e) {
            log.warn("Could not load notifications: {}", e.getMessage());
            model.addAttribute("notifications", List.of());
            model.addAttribute("status", status);
            model.addAttribute("countAll", 0L);
            model.addAttribute("countUnread", 0L);
            model.addAttribute("countRead", 0L);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 1);
            model.addAttribute("filteredCount", 0);
            model.addAttribute("warnMessage", "Notification service is temporarily unavailable.");
        }
        return "notifications";
    }

    private List<NotificationView> enrich(List<NotificationDto> notifications) {
        Map<UUID, MatchDto> matchCache = new HashMap<>();
        List<NotificationView> views = new ArrayList<>();
        for (NotificationDto n : notifications) {
            MatchDto match = null;
            if (n.getMatchId() != null) {
                match = matchCache.computeIfAbsent(n.getMatchId(), id -> {
                    try {
                        return matchService.findById(id);
                    } catch (Exception e) {
                        return null;
                    }
                });
            }
            views.add(new NotificationView(
                    n.getId(),
                    n.getMessage(),
                    n.getType(),
                    n.isRead(),
                    n.getCreatedAt(),
                    n.getMatchId(),
                    match != null ? match.getHomeTeamId() : null,
                    match != null ? teamLabel(match.getHomeTeamName(), match.getHomeTeamCity()) : null,
                    match != null ? match.getAwayTeamId() : null,
                    match != null ? teamLabel(match.getAwayTeamName(), match.getAwayTeamCity()) : null,
                    match != null ? match.getLeagueId() : null,
                    match != null ? match.getLeagueName() : null));
        }
        return views;
    }

    private String teamLabel(String name, String city) {
        return city != null ? name + " (" + city + ")" : name;
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(@RequestParam(required = false) String status,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            notificationClient.markAllRead(userId);
            redirectAttributes.addFlashAttribute("statusMessage", "All notifications marked as read.");
        } catch (Exception e) {
            log.warn("Could not mark all notifications read: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("warnMessage", "Could not mark notifications as read.");
        }
        return status != null && !status.isBlank()
                ? "redirect:/notifications?status=" + status
                : "redirect:/notifications";
    }

    @GetMapping("/notifications/subscriptions")
    public String subscriptionsPage(Authentication authentication, Model model) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            List<SubscriptionDto> subs = notificationClient.getSubscriptions(userId);
            model.addAttribute("subscriptions", subs.stream().map(this::buildView).toList());
        } catch (Exception e) {
            log.warn("Could not load subscriptions: {}", e.getMessage());
            model.addAttribute("subscriptions", List.of());
            model.addAttribute("warnMessage", "Notification service is temporarily unavailable.");
        }
        return "subscriptions";
    }

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

            Set<UUID> directLeagueIds = subs.stream()
                    .filter(s -> "LEAGUE".equals(s.getEntityType()))
                    .map(SubscriptionDto::getEntityId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Map<UUID, MatchDto> matchMap = new LinkedHashMap<>();
            for (UUID leagueId : leagueIds) {
                try {
                    leagueService.findDetail(leagueId).getMatches()
                            .forEach(m -> matchMap.put(m.getId(), m));
                } catch (Exception ignored) {}
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusDays(14);
            LocalDateTime liveThreshold = now.minusMinutes(90);

            List<MatchDto> live = matchMap.values().stream()
                    .filter(m -> !m.getPlayedAt().isAfter(now) && m.getPlayedAt().isAfter(liveThreshold))
                    .filter(m -> followedTeamIds.contains(m.getHomeTeamId())
                            || followedTeamIds.contains(m.getAwayTeamId()))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                    .toList();

            List<MatchDto> upcoming = matchMap.values().stream()
                    .filter(m -> m.getPlayedAt().isAfter(now))
                    .filter(m -> followedTeamIds.contains(m.getHomeTeamId())
                            || followedTeamIds.contains(m.getAwayTeamId()))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                    .toList();

            List<MatchDto> recent = matchMap.values().stream()
                    .filter(m -> !m.getPlayedAt().isAfter(liveThreshold) && m.getPlayedAt().isAfter(cutoff))
                    .filter(m -> followedTeamIds.contains(m.getHomeTeamId())
                            || followedTeamIds.contains(m.getAwayTeamId()))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt).reversed())
                    .toList();

            List<Map<String, Object>> liveMatchesForJs = live.stream()
                    .map(m -> {
                        List<Map<String, Object>> goals = m.getGoalTimeline().stream()
                                .map(g -> Map.<String, Object>of(
                                        "minute", g.getMinute() != null ? g.getMinute() : 0,
                                        "half", g.getHalf() != null ? g.getHalf().name() : "FIRST",
                                        "homeGoal", g.isHomeGoal(),
                                        "rh", g.getRunningHomeScore() != null ? g.getRunningHomeScore() : 0,
                                        "ra", g.getRunningAwayScore() != null ? g.getRunningAwayScore() : 0))
                                .toList();
                        return Map.<String, Object>of(
                                "id", m.getId().toString(),
                                "elapsedMin", Duration.between(m.getPlayedAt(), now).toMinutes(),
                                "elapsedSec", Duration.between(m.getPlayedAt(), now).getSeconds(),
                                "goals", goals);
                    })
                    .toList();
            Map<UUID, Long> elapsedByMatchId = live.stream()
                    .collect(Collectors.toMap(MatchDto::getId,
                            m -> Duration.between(m.getPlayedAt(), now).toMinutes()));

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

    @GetMapping("/notifications/unread-count")
    @ResponseBody
    public long unreadCount(Authentication authentication) {
        if (authentication == null) return 0;
        try {
            UUID userId = userService.findByUsername(authentication.getName()).getId();
            return notificationClient.getUnreadCount(userId);
        } catch (Exception e) {
            return 0;
        }
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
                    .filter(n -> !n.isRead())
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warnMessage", "Already following or notification service unavailable.");
        }
        return returnUrl != null ? "redirect:" + returnUrl : "redirect:/notifications";
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
        return returnUrl != null ? "redirect:" + returnUrl : "redirect:/notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable UUID id,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) Integer page,
                           Authentication authentication) {
        try {
            notificationClient.markRead(id);
        } catch (Exception e) {
            log.warn("Could not mark notification {} as read: {}", id, e.getMessage());
        }
        StringBuilder url = new StringBuilder("redirect:/notifications");
        if (status != null && !status.isBlank()) url.append("?status=").append(status);
        if (page != null && page > 0) url.append(url.indexOf("?") >= 0 ? "&" : "?").append("page=").append(page);
        return url.toString();
    }

    @DeleteMapping("/notifications/clear")
    public String clearAll(Authentication authentication, RedirectAttributes redirectAttributes) {
        UUID userId = userService.findByUsername(authentication.getName()).getId();
        try {
            notificationClient.clearAll(userId);
            redirectAttributes.addFlashAttribute("statusMessage", "All notifications cleared.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("warnMessage", "Could not clear notifications.");
        }
        return "redirect:/notifications";
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
                message = liveStatusMessage(match, now, home, away);
                type = "MATCH_UPDATE";
            }
            notificationClient.notifyUser(new NotifyRequest(userId, matchId, message, type));
        } catch (Exception e) {
            log.warn("Could not send instant status for match {}: {}", matchId, e.getMessage());
        }
    }

    private String liveStatusMessage(MatchDto match, LocalDateTime now, String home, String away) {
        long realMin = Duration.between(match.getPlayedAt(), now).toMinutes();
        String phase;
        int maxMin;
        if (realMin <= 20)      { phase = "FIRST";  maxMin = (int) realMin; }
        else if (realMin <= 25) { phase = "HT";     maxMin = 20; }
        else if (realMin <= 45) { phase = "SECOND"; maxMin = (int) (realMin - 25); }
        else                    { phase = "FT";     maxMin = 20; }

        int hs = 0, as = 0;
        for (GoalDto g : match.getGoalTimeline()) {
            int minute = g.getMinute() != null ? g.getMinute() : 0;
            int rh = g.getRunningHomeScore() != null ? g.getRunningHomeScore() : 0;
            int ra = g.getRunningAwayScore() != null ? g.getRunningAwayScore() : 0;
            boolean firstHalf = g.getHalf() != null && "FIRST".equals(g.getHalf().name());
            if (firstHalf) {
                if (!"FIRST".equals(phase) || minute <= maxMin) {
                    hs = rh;
                    as = ra;
                }
            } else {
                int secMax = "SECOND".equals(phase) ? maxMin : ("FT".equals(phase) ? 20 : -1);
                if (secMax >= 0 && minute <= secMax) {
                    hs = rh;
                    as = ra;
                }
            }
        }

        String display = switch (phase) {
            case "FIRST"  -> realMin + "'";
            case "HT"     -> "HT";
            case "SECOND" -> (20 + (realMin - 25)) + "'";
            default        -> "FT";
        };
        String prefix = "FT".equals(phase) ? "Full time: " : "LIVE: ";
        return prefix + home + " " + hs + ":" + as + " " + away + " · " + display;
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
                Integer position = null;
                for (int i = 0; i < league.getStandings().size(); i++) {
                    StandingRow row = league.getStandings().get(i);
                    if (s.getEntityId().equals(row.getTeamId())) {
                        position = i + 1;
                        break;
                    }
                }
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
