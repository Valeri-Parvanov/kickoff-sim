package com.kickoffsim.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied() {
        return "redirect:/";
    }

    @ExceptionHandler(StaleSessionException.class)
    public String handleStaleSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return "redirect:/login?expired";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("errorTitle", "Page not found");
        model.addAttribute("errorMessage", "The page you're looking for doesn't exist. Please check the address or use the links below.");
        return "error";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFound(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("errorMessage", "The requested item was not found. It may have been deleted or the link may be outdated.");
        return "error";
    }

    @ExceptionHandler(InvalidMatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidMatch(InvalidMatchException ex, Model model) {
        model.addAttribute("status", 400);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(SquadLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleSquadLimitExceeded(Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("errorMessage", "This team's squad is already full (maximum 12 players).");
        return "error";
    }

    @ExceptionHandler(DuplicateShirtNumberException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDuplicateShirtNumber(DuplicateShirtNumberException ex, Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(InvalidGoalException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidGoal(InvalidGoalException ex, Model model) {
        model.addAttribute("status", 400);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDuplicateEntry(HttpServletRequest request, HttpServletResponse response) {
        String referer = request.getHeader("Referer");
        String targetUrl = (referer != null && !referer.isEmpty()) ? referer : "/";

        FlashMap flashMap = new FlashMap();
        flashMap.put("errorMessage", "A record with this name already exists. Please choose a different name or add a city.");
        try {
            String path = new java.net.URI(targetUrl).getPath();
            flashMap.setTargetRequestPath(path);
        } catch (Exception ignored) {}

        var mgr = RequestContextUtils.getFlashMapManager(request);
        if (mgr != null) {
            mgr.saveOutputFlashMap(flashMap, request, response);
        }

        return new ModelAndView(new RedirectView(targetUrl, false));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, Model model) {
        log.error("Unhandled exception", ex);
        model.addAttribute("status", 500);
        model.addAttribute("errorMessage", "Something unexpected happened. Please try again or use the links below to navigate.");
        return "error";
    }
}
