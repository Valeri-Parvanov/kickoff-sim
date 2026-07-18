package bg.softuni.footballleague.web;

import bg.softuni.footballleague.client.NotificationClient;
import bg.softuni.footballleague.client.SubscriptionDto;
import bg.softuni.footballleague.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchFollowSupport {

    private final NotificationClient notificationClient;
    private final UserService userService;

    public Set<UUID> subscribedMatchIds(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Set.of();
        }
        try {
            UUID userId = userService.findByUsername(authentication.getName()).getId();
            return notificationClient.getSubscriptions(userId).stream()
                    .filter(s -> "MATCH".equals(s.getEntityType()))
                    .map(SubscriptionDto::getEntityId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Could not load match subscriptions: {}", e.getMessage());
            return Set.of();
        }
    }
}
