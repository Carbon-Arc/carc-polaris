package org.apache.polaris.core.metering;

/**
 * Exception thrown when metering service is unavailable or returns errors.
 */
public class MeteringServiceException extends Exception {
    public MeteringServiceException(String message) {
        super(message);
    }

    public MeteringServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
