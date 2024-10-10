package com.rmouduri.fixme;

public class FixMessageException extends Exception {
    public FixMessageException() {
        super();
    }

    public FixMessageException(String message) {
        super(message);
    }

    public FixMessageException(Throwable cause) {
        super(cause);
    }
}
