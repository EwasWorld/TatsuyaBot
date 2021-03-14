package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;


/**
 * updated style 10/12/18
 */
public class LockCommand extends AbstractCommand {
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

        Bot.setIsLocked(true);
        sendMessage(event.getChannel(), "Locked");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return "lock";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "lock the bot to prevent commands from going through";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Rank getRequiredRank() {
        return Rank.ADMIN;
    }
}
