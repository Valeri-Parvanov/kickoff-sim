package com.kickoffsim.web;

import com.kickoffsim.service.ChangeRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResubmitSupportTest {

    @Mock private ChangeRequestService changeRequestService;

    private final Authentication authentication = mock(Authentication.class);

    @Test
    void addRejectionBanner_nullReason_setsFromRequestButNoErrorMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.getRejectionReason(id, authentication)).thenReturn(null);
        Model model = new ExtendedModelMap();

        ResubmitSupport.addRejectionBanner(model, changeRequestService, id, authentication);

        assertThat(model.getAttribute("fromRequest")).isEqualTo(id);
        assertThat(model.getAttribute("errorMessage")).isNull();
    }

    @Test
    void addRejectionBanner_blankReason_noErrorMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.getRejectionReason(id, authentication)).thenReturn("   ");
        Model model = new ExtendedModelMap();

        ResubmitSupport.addRejectionBanner(model, changeRequestService, id, authentication);

        assertThat(model.getAttribute("errorMessage")).isNull();
    }

    @Test
    void addRejectionBanner_realReason_setsErrorMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.getRejectionReason(id, authentication)).thenReturn("Team name already exists");
        Model model = new ExtendedModelMap();

        ResubmitSupport.addRejectionBanner(model, changeRequestService, id, authentication);

        assertThat(model.getAttribute("errorMessage")).isEqualTo("Rejected: Team name already exists");
    }
}
