package com.kickoffsim.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class FlashDialogRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusMessage_rendersSuccessDialogInsteadOfInlineBanner() throws Exception {
        mockMvc.perform(get("/login").flashAttr("statusMessage", "flash.profile.passwordchanged"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"flashDialog\"")))
                .andExpect(content().string(containsString("fd-success")))
                .andExpect(content().string(not(containsString("class=\"status-message\""))));
    }

    @Test
    void messageKey_isResolvedToEnglishByDefaultLocale() throws Exception {
        mockMvc.perform(get("/login").flashAttr("statusMessage", "flash.profile.passwordchanged"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password changed successfully.")))
                .andExpect(content().string(not(containsString("flash.profile.passwordchanged"))));
    }

    @Test
    void messageKey_isResolvedToBulgarianWhenLangIsBg() throws Exception {
        mockMvc.perform(get("/login").param("lang", "bg")
                        .flashAttr("statusMessage", "flash.profile.passwordchanged"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Паролата е сменена успешно.")));
    }

    @Test
    void messageKey_isResolvedToGermanWhenLangIsDe() throws Exception {
        mockMvc.perform(get("/login").param("lang", "de")
                        .flashAttr("statusMessage", "flash.profile.passwordchanged"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Passwort erfolgreich geändert.")));
    }

    @Test
    void unknownKey_fallsBackToTheRawText() throws Exception {
        mockMvc.perform(get("/login").flashAttr("errorMessage", "Cannot delete 'Vihor' — it belongs to league 'Cup'."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fd-error")))
                .andExpect(content().string(containsString("Cannot delete &#39;Vihor&#39;")));
    }

    @Test
    void warnMessage_rendersWarnDialog() throws Exception {
        mockMvc.perform(get("/login").flashAttr("warnMessage", "flash.league.invalidformat"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fd-warn")))
                .andExpect(content().string(containsString("Invalid league format.")));
    }

    @Test
    void scheduleError_rendersErrorDialog() throws Exception {
        mockMvc.perform(get("/login").flashAttr("scheduleError", "Schedule already exists."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fd-error")))
                .andExpect(content().string(containsString("Schedule already exists.")));
    }

    @Test
    void errorOutranksSuccess_whenBothArePresent() throws Exception {
        mockMvc.perform(get("/login")
                        .flashAttr("statusMessage", "flash.league.created")
                        .flashAttr("scheduleError", "Schedule failed."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fd-error")))
                .andExpect(content().string(containsString("League created.")))
                .andExpect(content().string(containsString("Schedule failed.")));
    }

    @Test
    void noFlashAttributes_rendersNoDialog() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"flashDialog\""))));
    }
}
