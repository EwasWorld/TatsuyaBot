package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;


/**
 * updated style 10/12/18
 */
public class PingCommand extends AbstractCommand {
    /**
     * {@inheritDoc}
     */
    @Override
    public HelpCommand.HelpVisibility getHelpVisibility() {
        return HelpCommand.HelpVisibility.NORMAL;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
        checkPermission(event.getMember());
        sendMessage(event.getChannel(), "Pong");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return "ping";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "test Juuzo is responding";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Rank getRequiredRank() {
        return Rank.USER;
    }
}
