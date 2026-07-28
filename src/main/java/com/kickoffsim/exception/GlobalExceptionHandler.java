package com.kickoffsim.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import java.util.stream.Collectors;

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
        model.addAttribute("errorMessage", "flash.error.pagenotfound");
        return "error";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFound(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("errorMessage", "flash.error.itemnotfound");
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
        model.addAttribute("errorMessage", "flash.error.squadfull");
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

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleConstraintViolation(ConstraintViolationException ex, Model model) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        model.addAttribute("status", 400);
        model.addAttribute("errorMessage", message.isBlank() ? "flash.error.unexpected" : message);
        return "error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDuplicateEntry(HttpServletRequest request, HttpServletResponse response) {
        String referer = request.getHeader("Referer");
        String targetUrl = (referer != null && !referer.isEmpty()) ? referer : "/";

        FlashMap flashMap = new FlashMap();
        flashMap.put("errorMessage", "flash.error.duplicatename");
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

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseBody
    public void handleDisconnectedClient(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected before the response was written: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, Model model) {
        log.error("Unhandled exception", ex);
        model.addAttribute("status", 500);
        model.addAttribute("errorMessage", "flash.error.unexpected");
        return "error";
    }
}
