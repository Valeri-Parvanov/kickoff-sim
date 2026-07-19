package com.kickoffsim.exception;

public class StaleSessionException extends RuntimeException {

    public StaleSessionException(String message) {
        super(message);
    }
}
