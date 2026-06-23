package ch.tarvynanalytics.graphs.algos.exception;

/**
 * Base unchecked exception for all errors raised by the comparability library.
 */
public class ComparabilityException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message
     */
    public ComparabilityException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ComparabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
