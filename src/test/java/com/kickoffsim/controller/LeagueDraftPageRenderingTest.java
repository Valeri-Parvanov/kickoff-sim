package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.service.LeagueDraftService;
import com.kickoffsim.web.LeagueDraftSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class LeagueDraftPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueDraftService leagueDraftService;

    @Test
    void generatedDraft_rendersThroughTheWizardTemplate() throws Exception {
        when(leagueDraftService.draft(anyInt(), any())).thenReturn(draftedForm());

        mockMvc.perform(get("/leagues/wizard/ai").param("format", "8"))
                .andExpect(status().isOk())
                .andExpect(view().name("leagues/wizard-new-teams"));
    }

    @Test
    void blankWizard_stillRendersWithTheAiEntryPoint() throws Exception {
        mockMvc.perform(get("/leagues/wizard/new-teams").param("format", "8"))
                .andExpect(status().isOk())
                .andExpect(view().name("leagues/wizard-new-teams"));
    }

    private LeagueWizardForm draftedForm() {
        LeagueWizardForm form = new LeagueWizardForm();
        form.setFormat(8);
        form.setLeagueName("Banitsa Cup");

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(null, 8, List.of(), (name, city) -> false);
        for (int i = 0; i < teams.size(); i++) {
            LeagueDraftSanitizer.fillSquad(teams.get(i), null, 12, i);
        }
        form.getNewTeams().addAll(teams);
        return form;
    }
}
