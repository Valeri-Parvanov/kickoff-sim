package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Team;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    @Override
    @EntityGraph(attributePaths = "league")
    List<Team> findAll(Sort sort);

    @EntityGraph(attributePaths = "league")
    List<Team> findAllByLeague(League league);
}
