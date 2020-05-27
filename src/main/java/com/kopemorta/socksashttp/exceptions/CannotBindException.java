package com.kopemorta.socksashttp.exceptions;

public class CannotBindException extends Exception {
    public CannotBindException() {
    }

    public CannotBindException(String message) {
        super(message);
    }

    public CannotBindException(String message, Throwable cause) {
        super(message, cause);
    }

    public CannotBindException(Throwable cause) {
        super(cause);
    }

    public CannotBindException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
