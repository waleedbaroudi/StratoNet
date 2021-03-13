package utils;

/**
 * throws when a token with an incorrect format is detected
 */
public class InvalidTokenException extends IllegalArgumentException {
    public InvalidTokenException() {
        super("Wrong token length.");
    }
}
