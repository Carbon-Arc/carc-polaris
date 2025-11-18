package org.apache.polaris.core.metering;

/**
 * Exception thrown when principal has insufficient token balance to proceed with a request.
 */
public class InsufficientBalanceException extends Exception {
    public InsufficientBalanceException() {
        super("Insufficient tokens. Please purchase more tokens to access data.");
    }

    public InsufficientBalanceException(Throwable cause) {
        super("Insufficient tokens. Please purchase more tokens to access data.", cause);
    }
}
