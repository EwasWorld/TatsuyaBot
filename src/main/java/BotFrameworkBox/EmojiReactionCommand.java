package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;

/**
 * Commands that will react to an emoji being added to a message
 *
 * created 21/7/20
 */
public interface EmojiReactionCommand {
    /**
     * Executes the command associated with given emoji being added. Does nothing if there is no command associated with
     * the emoji or if the message does not belong to this class. NOTE: This is only triggered on bot messages
     *
     * @return true if the message belonged to this class (whether the reaction was used or not)
     */
    boolean executeFromAddReaction(GenericGuildMessageReactionEvent event);

    /**
     * Executes the command associated with given emoji being removed. Does nothing if there is no command associated
     * with the emoji or if the message does not belong to this class. NOTE: This is only triggered on bot messages
     *
     * @return true if the message belonged to this class (whether the reaction was used or not)
     */
    boolean executeFromRemoveReaction(GenericGuildMessageReactionEvent event);
}
