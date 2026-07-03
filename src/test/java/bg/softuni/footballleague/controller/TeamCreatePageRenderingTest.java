package bg.softuni.footballleague.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class TeamCreatePageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createPage_rendersWithoutError() throws Exception {
        mockMvc.perform(get("/teams/form"))
                .andExpect(status().isOk())
                .andExpect(view().name("teams/create"));
    }
}
