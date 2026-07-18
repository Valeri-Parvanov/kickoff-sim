package com.kickoffsim.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotFoundAccessDeniedHandlerTest {

    @Test
    void handle_sendsNotFoundError() throws Exception {
        NotFoundAccessDeniedHandler handler = new NotFoundAccessDeniedHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(response).sendError(404);
    }
}
