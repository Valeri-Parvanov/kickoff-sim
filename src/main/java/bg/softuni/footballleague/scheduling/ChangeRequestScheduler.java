package bg.softuni.footballleague.scheduling;

import bg.softuni.footballleague.service.ChangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChangeRequestScheduler {

    private static final int STALE_PENDING_DAYS = 14;
    private static final int RESOLVED_RETENTION_DAYS = 30;

    private final ChangeRequestService changeRequestService;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldResolvedRequests() {
        changeRequestService.purgeResolvedOlderThan(RESOLVED_RETENTION_DAYS);
    }

    @Scheduled(initialDelay = 300_000, fixedRate = 21_600_000)
    public void expireStalePendingRequests() {
        changeRequestService.expireStalePending(STALE_PENDING_DAYS);
    }
}
