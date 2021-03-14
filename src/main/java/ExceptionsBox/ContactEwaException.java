package ExceptionsBox;

/**
 * created 13/11/18
 */
public class ContactEwaException extends BadStateException {
    public ContactEwaException() {
        super();
    }


    public ContactEwaException(String s) {
        super(s + "\nThis is very bad, contact Ewa");
    }
}
