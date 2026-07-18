package bg.softuni.footballleague.exception;

public class SquadLimitExceededException extends RuntimeException {

    public SquadLimitExceededException(String message) {
        super(message);
    }
}
