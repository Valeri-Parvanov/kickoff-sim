package com.kickoffsim.controller;

import com.kickoffsim.dto.ChangeRequestView;
import com.kickoffsim.exception.ChangeRequestApprovalException;
import com.kickoffsim.model.ChangeAction;
import com.kickoffsim.model.ChangeRequestStatus;
import com.kickoffsim.model.EntityType;
import com.kickoffsim.service.ChangeRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyChangeRequestControllerTest {

    @Mock private ChangeRequestService changeRequestService;

    @InjectMocks private MyChangeRequestController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void myProposals_returnsView() {
        when(changeRequestService.findMine(auth)).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.myProposals(auth, model, "ALL", "ALL", 0)).isEqualTo("my-change-requests");
        assertThat(model.getAttribute("totalCount")).isEqualTo(0);
        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0));
    }

    @Test
    void myProposals_filtersByStatus() {
        when(changeRequestService.findMine(auth)).thenReturn(List.of(
                view(ChangeRequestStatus.PENDING, EntityType.TEAM),
                view(ChangeRequestStatus.REJECTED, EntityType.TEAM),
                view(ChangeRequestStatus.APPROVED, EntityType.TEAM)));
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "PENDING", "ALL", 0);

        assertThat(model.getAttribute("filteredCount")).isEqualTo(1);
        assertThat(model.getAttribute("pendingCount")).isEqualTo(1L);
        assertThat(model.getAttribute("rejectedCount")).isEqualTo(1L);
        assertThat(model.getAttribute("approvedCount")).isEqualTo(1L);
    }

    @Test
    void myProposals_filtersByType() {
        when(changeRequestService.findMine(auth)).thenReturn(List.of(
                view(ChangeRequestStatus.PENDING, EntityType.TEAM),
                view(ChangeRequestStatus.PENDING, EntityType.PLAYER)));
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "ALL", "PLAYER", 0);

        assertThat(model.getAttribute("filteredCount")).isEqualTo(1);
    }

    @Test
    void myProposals_pageBeyondBounds_clampsToLastPage() {
        List<ChangeRequestView> views = new ArrayList<>();
        for (int i = 0; i < 15; i++) views.add(view(ChangeRequestStatus.PENDING, EntityType.TEAM));
        when(changeRequestService.findMine(auth)).thenReturn(views);
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "ALL", "ALL", 999);

        assertThat(model.getAttribute("currentPage")).isEqualTo(1);
        assertThat(model.getAttribute("totalPages")).isEqualTo(2);
        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, 1));
    }

    @Test
    void myProposals_negativePage_clampsToZero() {
        List<ChangeRequestView> views = new ArrayList<>();
        for (int i = 0; i < 15; i++) views.add(view(ChangeRequestStatus.PENDING, EntityType.TEAM));
        when(changeRequestService.findMine(auth)).thenReturn(views);
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "ALL", "ALL", -5);

        assertThat(model.getAttribute("currentPage")).isEqualTo(0);
    }

    @Test
    void myProposals_manyPages_middlePage_showsBothEllipses() {
        List<ChangeRequestView> views = new ArrayList<>();
        for (int i = 0; i < 100; i++) views.add(view(ChangeRequestStatus.PENDING, EntityType.TEAM));
        when(changeRequestService.findMine(auth)).thenReturn(views);
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "ALL", "ALL", 5);

        assertThat(model.getAttribute("totalPages")).isEqualTo(10);
        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, -1, 3, 4, 5, 6, 7, -1, 9));
    }

    @Test
    void myProposals_manyPages_firstPage_showsOnlyTrailingEllipsis() {
        List<ChangeRequestView> views = new ArrayList<>();
        for (int i = 0; i < 100; i++) views.add(view(ChangeRequestStatus.PENDING, EntityType.TEAM));
        when(changeRequestService.findMine(auth)).thenReturn(views);
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "ALL", "ALL", 0);

        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, 1, 2, -1, 9));
    }

    @Test
    void myProposals_manyPages_lastPage_showsOnlyLeadingEllipsis() {
        List<ChangeRequestView> views = new ArrayList<>();
        for (int i = 0; i < 100; i++) views.add(view(ChangeRequestStatus.PENDING, EntityType.TEAM));
        when(changeRequestService.findMine(auth)).thenReturn(views);
        Model model = new ExtendedModelMap();

        controller.myProposals(auth, model, "ALL", "ALL", 20);

        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, -1, 7, 8, 9));
    }

    private ChangeRequestView view(ChangeRequestStatus status, EntityType entityType) {
        ChangeRequestView v = new ChangeRequestView();
        v.setId(UUID.randomUUID());
        v.setStatus(status);
        v.setEntityType(entityType);
        v.setAction(ChangeAction.CREATE);
        return v;
    }

    @Test
    void cancel_success_setsStatusMessage() {
        UUID id = UUID.randomUUID();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.cancel(id, auth, ra)).isEqualTo("redirect:/my-change-requests");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Request cancelled.");
    }

    @Test
    void cancel_approvalException_setsError() {
        UUID id = UUID.randomUUID();
        doThrow(new ChangeRequestApprovalException("nope"))
                .when(changeRequestService).cancelMine(eq(id), any());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.cancel(id, auth, ra)).isEqualTo("redirect:/my-change-requests");
        assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    }
}
