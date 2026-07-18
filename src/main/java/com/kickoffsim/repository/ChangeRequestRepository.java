package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.ChangeRequest;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import bg.softuni.footballleague.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    List<ChangeRequest> findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus status);

    List<ChangeRequest> findAllByRequestedByOrderByRequestedAtDesc(User requestedBy);

    long countByStatus(ChangeRequestStatus status);

    long countByStatusAndRequestedBy(ChangeRequestStatus status, User requestedBy);

    List<ChangeRequest> findAllByStatusAndRequestedAtBefore(ChangeRequestStatus status, LocalDateTime cutoff);

    long deleteByStatusInAndReviewedAtBefore(Collection<ChangeRequestStatus> statuses, LocalDateTime cutoff);
}
