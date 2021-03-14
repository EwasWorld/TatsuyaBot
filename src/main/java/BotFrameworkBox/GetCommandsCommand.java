package BotFrameworkBox;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import static BotFrameworkBox.HelpCommand.getExecuteHelpVisibility;


/**
 * updated style 10/12/18
 */
public class GetCommandsCommand extends AbstractCommand {
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

        final Rank rank = getRank(event.getMember());
        final HelpCommand.HelpVisibility helpVisibility = getExecuteHelpVisibility(args);
        StringBuilder commandString = new StringBuilder("Possible commands: *(for more detail use !help)* \n");
        for (AbstractCommand command : Bot.getCommands()) {
            if (rank.hasPermission(command.getRequiredRank()) && command.getHelpVisibility() == helpVisibility) {
                commandString.append(command.getCommand()).append(", ");
            }
        }

        commandString.delete(commandString.lastIndexOf(","), commandString.lastIndexOf(",") + 1);
        sendMessage(event.getChannel(), commandString.toString());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return "commands";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "List possible commands without descriptions";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Rank getRequiredRank() {
        return Rank.USER;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getArguments() {
        return "[char]";
    }
}
