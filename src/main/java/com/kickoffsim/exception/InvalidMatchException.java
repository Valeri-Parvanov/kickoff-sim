package bg.softuni.footballleague.exception;

public class InvalidMatchException extends RuntimeException {

    public InvalidMatchException(String message) {
        super(message);
    }
}
