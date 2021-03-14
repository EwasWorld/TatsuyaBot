package ExceptionsBox;

// TODO Implement these
public class FeatureUnavailableException extends BadStateException {
    private static String message = "This feature is currently unavailable, complain at Ewa if you really want it";


    public FeatureUnavailableException() {
        super(message);
    }


    public FeatureUnavailableException(String featureDescription) {
        super(message + ". What would do:\n" + featureDescription);
    }
}
