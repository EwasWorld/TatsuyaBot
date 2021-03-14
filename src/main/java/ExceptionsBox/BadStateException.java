package ExceptionsBox;

public class BadStateException extends IllegalStateException {
    public BadStateException() {
        super();
    }


    public BadStateException(String s) {
        super(s);
    }
}
