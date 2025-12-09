package com.jreg.exception;

/**
 * Base exception for all OCI-related errors.
 * Maps to OCI error codes in the Distribution Spec.
 */
public class OciException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    public OciException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public OciException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}
