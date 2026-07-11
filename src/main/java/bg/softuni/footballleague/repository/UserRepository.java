package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailAndIdNot(String email, UUID id);

    long countByRole(Role role);
}
