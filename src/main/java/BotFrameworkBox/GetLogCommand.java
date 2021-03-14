package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;


/**
 * updated style 10/12/18
 */
public class GetLogCommand extends AbstractCommand {
    /**
     * {@inheritDoc}
     */
    @Override
    public HelpCommand.HelpVisibility getHelpVisibility() {
        return HelpCommand.HelpVisibility.ADMIN;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
        checkPermission(event.getMember());

        // TODO Fix send log file
//        final Message message = new MessageBuilder().append("Log").build();
//        event.getChannel().sendFile(Logger.getLoggedEventsToSend(), message).queue();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return "getLog";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "returns a file of the log";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Rank getRequiredRank() {
        return Rank.CREATOR;
    }
}
