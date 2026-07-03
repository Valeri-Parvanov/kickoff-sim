package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class SquadPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Test
    void squadPage_rendersWithoutError() throws Exception {
        League league = new League();
        league.setName("Test League " + UUID.randomUUID());
        league.setCountry("Bulgaria");
        league = leagueRepository.save(league);

        Team team = new Team();
        team.setName("Test FC " + UUID.randomUUID());
        team.setCity("Sofia");
        team.setLeague(league);
        team = teamRepository.save(team);

        TestTransaction.flagForCommit();
        TestTransaction.end();
        UUID teamId = team.getId();

        try {
            mockMvc.perform(get("/teams/{id}/squad", teamId))
                    .andExpect(status().isOk())
                    .andExpect(view().name("teams/squad"));
        } finally {
            TestTransaction.start();
            teamRepository.deleteById(teamId);
            leagueRepository.deleteById(league.getId());
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }
}
