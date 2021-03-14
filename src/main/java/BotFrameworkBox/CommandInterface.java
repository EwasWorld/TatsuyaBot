package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;


/**
 * created 10/12/2018
 */
public interface CommandInterface {
    /**
     * Action to be taken when the given command is used
     *
     * @param args args from the message not including the command string
     */
    void execute(@NotNull String args, @NotNull MessageReceivedEvent event);


    /**
     * @return The string which invokes the command in the chat
     */
    String getCommand();


    /**
     * @return A description of the command for the user
     */
    String getDescription();


    /**
     * @return The rank that is needed to use this command
     */
    AbstractCommand.Rank getRequiredRank();


    /**
     * @return Arguments which can come after the command name. Separated by / in the form secondary command {required}
     * [optional]. "" for no arguments. e.g. {new/end} or add {number} / get [number]
     */
    String getArguments();


    /*
     * TODO Command aliases
     * @return Other strings which can invoke the command in the chat
     */
//    String[] commandAliases();

    // TODO Example
}
