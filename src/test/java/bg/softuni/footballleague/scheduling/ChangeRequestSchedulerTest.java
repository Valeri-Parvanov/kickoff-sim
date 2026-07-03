package bg.softuni.footballleague.scheduling;

import bg.softuni.footballleague.service.ChangeRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChangeRequestSchedulerTest {

    @Mock private ChangeRequestService changeRequestService;

    @InjectMocks
    private ChangeRequestScheduler scheduler;

    @Test
    void purgeOldResolvedRequests_delegatesToService() {
        scheduler.purgeOldResolvedRequests();
        verify(changeRequestService).purgeResolvedOlderThan(anyInt());
    }

    @Test
    void expireStalePendingRequests_delegatesToService() {
        scheduler.expireStalePendingRequests();
        verify(changeRequestService).expireStalePending(anyInt());
    }
}
