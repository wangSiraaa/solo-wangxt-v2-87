package org.fictfish.ledger.web;

import org.springframework.http.HttpStatus;

/** Business rule violation. code is a stable machine-readable string. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException duplicate(String message) {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE", message);
    }

    public static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID", message);
    }

    public static ApiException quotaExceeded(String message) {
        return new ApiException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
