package ExceptionsBox;

public class BadUserInputException extends IllegalArgumentException {
    public BadUserInputException() {
        super();
    }


    public BadUserInputException(String s) {
        super(s);
    }
}
