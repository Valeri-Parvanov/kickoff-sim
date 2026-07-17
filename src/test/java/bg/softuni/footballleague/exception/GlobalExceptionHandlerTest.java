package bg.softuni.footballleague.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAccessDenied_redirectsHome() {
        assertThat(handler.handleAccessDenied()).isEqualTo("redirect:/");
    }

    @Test
    void handleStaleSession_clearsContextAndRedirects() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        assertThat(handler.handleStaleSession(request)).isEqualTo("redirect:/login?expired");
    }

    @Test
    void handleNoResource_returnsErrorViewWith404() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleNoResource(model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(404);
        assertThat(model.getAttribute("errorTitle")).isEqualTo("Page not found");
    }

    @Test
    void handleEntityNotFound_returnsErrorViewWith404() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleEntityNotFound(model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(404);
    }

    @Test
    void handleInvalidMatch_returnsErrorViewWith400AndMessage() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleInvalidMatch(new InvalidMatchException("bad match"), model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(400);
        assertThat(model.getAttribute("errorMessage")).isEqualTo("bad match");
    }

    @Test
    void handleSquadLimitExceeded_returnsErrorViewWith409() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleSquadLimitExceeded(model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(409);
    }

    @Test
    void handleDuplicateShirtNumber_returnsErrorViewWith409AndMessage() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleDuplicateShirtNumber(new DuplicateShirtNumberException("dup #7"), model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(409);
        assertThat(model.getAttribute("errorMessage")).isEqualTo("dup #7");
    }

    @Test
    void handleInvalidGoal_returnsErrorViewWith400AndMessage() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleInvalidGoal(new InvalidGoalException("bad goal"), model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(400);
        assertThat(model.getAttribute("errorMessage")).isEqualTo("bad goal");
    }

    @Test
    void handleGeneric_returnsErrorViewWith500() {
        Model model = new ExtendedModelMap();

        assertThat(handler.handleGeneric(new RuntimeException("boom"), model)).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(500);
        assertThat(model.getAttribute("errorMessage")).isNotNull();
    }

    @Test
    void handleDuplicateEntry_redirectsToReferer() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Referer")).thenReturn("/teams");

        ModelAndView mav = handler.handleDuplicateEntry(request, response);

        assertThat(mav.getView()).isNotNull();
    }

    @Test
    void handleDuplicateEntry_nullReferer_redirectsHome() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Referer")).thenReturn(null);

        assertThat(handler.handleDuplicateEntry(request, response).getView()).isNotNull();
    }

    @Test
    void handleDuplicateEntry_withFlashMapManager_savesFlashMap() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Referer")).thenReturn("/teams");
        org.springframework.web.servlet.FlashMapManager manager =
                mock(org.springframework.web.servlet.FlashMapManager.class);
        when(request.getAttribute(org.springframework.web.servlet.DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE))
                .thenReturn(manager);

        assertThat(handler.handleDuplicateEntry(request, response).getView()).isNotNull();
        org.mockito.Mockito.verify(manager)
                .saveOutputFlashMap(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(request),
                        org.mockito.ArgumentMatchers.eq(response));
    }

    @Test
    void handleDuplicateEntry_invalidRefererUri_ignoredGracefully() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Referer")).thenReturn("http://bad uri/with space");

        assertThat(handler.handleDuplicateEntry(request, response).getView()).isNotNull();
    }

    @Test
    void handleDuplicateEntry_emptyReferer_redirectsHome() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Referer")).thenReturn("");

        assertThat(handler.handleDuplicateEntry(request, response).getView()).isNotNull();
    }

    @Test
    void handleStaleSession_withActiveSession_invalidatesIt() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        jakarta.servlet.http.HttpSession session = mock(jakarta.servlet.http.HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        assertThat(handler.handleStaleSession(request)).isEqualTo("redirect:/login?expired");
        org.mockito.Mockito.verify(session).invalidate();
    }
}
