package ch.tarvynanalytics.graphs.algos.exception;

/**
 * Thrown when the input matrix or its parameters are malformed: not square,
 * ragged, of inconsistent dimensions, or containing non-finite values. The
 * offending values are reported in brackets, e.g. {@code "... got [3x0]"}.
 */
public class InvalidInputException extends ComparabilityException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message
     */
    public InvalidInputException(String message) {
        super(message);
    }
}
