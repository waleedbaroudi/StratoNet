package utils;

public class InvalidTokenException extends IllegalArgumentException {
    public InvalidTokenException() {
        super("Wrong token length.");
    }
}
