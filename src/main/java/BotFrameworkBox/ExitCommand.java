package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;


/**
 * updated style 10/12/18
 */
public class ExitCommand extends AbstractCommand {
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
        sendMessage(event.getChannel(), "Bye bye :c");
        System.exit(0);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return "exit";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Save and exit the box";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Rank getRequiredRank() {
        return Rank.ADMIN;
    }
}
