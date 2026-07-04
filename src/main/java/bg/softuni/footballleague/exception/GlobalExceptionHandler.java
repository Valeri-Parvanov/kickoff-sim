package bg.softuni.footballleague.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public String handleNoResource(Model model) {
        model.addAttribute("errorTitle", "Page not found");
        model.addAttribute("errorMessage", "The page you're looking for doesn't exist. Please check the address or use the links below.");
        return "error";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleEntityNotFound(Model model) {
        model.addAttribute("errorMessage", "The requested item was not found. It may have been deleted or the link may be outdated.");
        return "error";
    }

    @ExceptionHandler(InvalidMatchException.class)
    public String handleInvalidMatch(InvalidMatchException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(SquadLimitExceededException.class)
    public String handleSquadLimitExceeded(Model model) {
        model.addAttribute("errorMessage", "This team's squad is already full (maximum 12 players).");
        return "error";
    }

    @ExceptionHandler(DuplicateShirtNumberException.class)
    public String handleDuplicateShirtNumber(DuplicateShirtNumberException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(InvalidGoalException.class)
    public String handleInvalidGoal(InvalidGoalException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDuplicateEntry(Model model) {
        model.addAttribute("errorMessage", "A record with that name already exists. Please choose a different name.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model) {
        log.error("Unhandled exception", ex);
        model.addAttribute("errorMessage", "Something unexpected happened. Please try again or use the links below to navigate.");
        return "error";
    }
}
