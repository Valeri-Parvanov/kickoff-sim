package bg.softuni.footballleague.exception;

public class StaleSessionException extends RuntimeException {

    public StaleSessionException(String message) {
        super(message);
    }
}
