package ExceptionsBox;

public class IncorrectPermissionsException extends IllegalStateException {
    public IncorrectPermissionsException() {
        super("You're not high enough rank to use this command");
    }


    public IncorrectPermissionsException(String s) {
        super(s);
    }
}
