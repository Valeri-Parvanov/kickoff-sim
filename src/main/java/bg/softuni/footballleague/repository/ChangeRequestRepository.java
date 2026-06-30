package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.ChangeRequest;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    List<ChangeRequest> findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus status);
}
