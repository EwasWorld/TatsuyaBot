package BotFrameworkBox;

/**
 * Commands that will react to a message being deleted
 *
 * created 21/7/20
 */
public interface MessageDeletedCommand {
    /**
     * @param messageId ID of the message that was deleted
     * @return true if the message belonged to this method (whether any action was performed or not)
     */
    boolean onMessageDeleted(Long messageId);
}
