package BotFrameworkBox;


import CoreBox.IDs;
import ExceptionsBox.BadStateException;
import ExceptionsBox.BadUserInputException;
import ExceptionsBox.IncorrectPermissionsException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.reflections.Reflections;

import javax.security.auth.login.LoginException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;


public class Bot {
    // TODO Make this changable
    public static final String commandPrefix = "!";
    // private static String resourceFilePath = pathToJuuzoBot + "src/main/resources/";
    private static final Map<String, AbstractCommand> commands = new HashMap<>();
    // Database and other information is stored in this location
    private static String pathToTatsuyaBot = IDs.pathToTatsuyaBot;
    // TODO Temporary file path while I figure out how to get the ideal one (commented out underneath) working
    private static String resourceFilePath = "resources/";
    // Prevents anyone other than me from using the bot
    private static boolean isLocked = false;

    public static void main(String[] args) {
        // Change the path to the specified one rather than using the default one
        if (args.length != 0) {
            pathToTatsuyaBot = args[0];
            resourceFilePath = pathToTatsuyaBot + resourceFilePath;
        }
        startJDA();
        loadCommands("BotFrameworkBox");
        loadCommands(IDs.customCommandsBox);
        DatabaseWrapper.setDatabaseEntryTypes(IDs.databaseEntryTypes);
    }


    /*
     * Turns the bot online in discord
     */
    private static void startJDA() {
        final JDABuilder builder = JDABuilder.createDefault(IDs.botToken);
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.DO_NOT_DISTURB);
        try {
            final JDA jda = builder.build();
            jda.addEventListener(new CommandListener());
        }
        catch (LoginException e) {
            System.err.println(e);
        }
    }


    /*
     * Instantiate each command from the CommandsBox and add it to the commands map
     */
    private static void loadCommands(String containingPackage) {
        final Reflections reflections = new Reflections(containingPackage);
        final Set<Class<? extends AbstractCommand>> classes = reflections.getSubTypesOf(AbstractCommand.class);
        for (Class<? extends AbstractCommand> s : classes) {
            try {
                if (Modifier.isAbstract(s.getModifiers())) {
                    continue;
                }
                final AbstractCommand c = s.getConstructor().newInstance();
                if (!commands.containsKey(c.getCommand())) {
                    commands.put(c.getCommand().toUpperCase(), c);
                }
            }
            catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                    NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }


    public static String getPathToTatsuyaBot() {
        return pathToTatsuyaBot;
    }


    public static String getResourceFilePath() {
        return resourceFilePath;
    }


    public static boolean isIsLocked() {
        return isLocked;
    }


    public static void setIsLocked(boolean isLocked) {
        Bot.isLocked = isLocked;
    }


    public static Set<AbstractCommand> getCommands() {
        return new HashSet<>(commands.values());
    }


    private static class CommandListener extends ListenerAdapter {
        /**
         * Removes the command string and the commandPrefix from the start of the message and returns the remainder
         */
        private static String getRemainingMessage(String command, String message) {
            message = message.substring(commandPrefix.length());
            if (!message.equalsIgnoreCase(command)) {
                return message.substring(command.length() + 1);
            }
            else {
                return "";
            }
        }

        @Override
        public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
            super.onGuildMessageDelete(event);
            for (AbstractCommand command : commands.values()) {
                if (command instanceof MessageDeletedCommand && ((MessageDeletedCommand) command)
                        .onMessageDeleted(event.getMessageIdLong())) {
                    break;
                }
            }
        }

        @Override
        public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
            super.onGuildMessageReactionAdd(event);
            if (!event.getMember().getUser().isBot()) {
                for (AbstractCommand command : commands.values()) {
                    if (command instanceof EmojiReactionCommand && ((EmojiReactionCommand) command)
                            .executeFromAddReaction(event)) {
                        break;
                    }
                }
            }
        }

        @Override
        public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
            super.onGuildMessageReactionRemove(event);
            if (!event.getMember().getUser().isBot()) {
                for (AbstractCommand command : commands.values()) {
                    if (command instanceof EmojiReactionCommand && ((EmojiReactionCommand) command)
                            .executeFromRemoveReaction(event)) {
                        break;
                    }
                }
            }
        }

        /*
         * Logs messages for use with Quotes
         *      then if the message begins with '!' and a known command it executes the command
         */
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            super.onMessageReceived(event);
            /*
             * PMs
             */
            if (event.isFromType(ChannelType.PRIVATE) && !event.getAuthor().isBot()) {
            }

            /*
             * Channel messages
             */
            String args = event.getMessage().getContentRaw();
            if (event.getAuthor().isBot() || !args.startsWith(commandPrefix)) {
                return;
            }
            final String command = args.substring(1).split(" ")[0].toUpperCase();
            args = getRemainingMessage(command, args);

            try {
                if (commands.size() == 0) {
                    throw new BadStateException("I'm so broken right now I just can't even");
                }
                if (!commands.containsKey(command)) {
                    throw new BadUserInputException("I have no memory of this command (" + commandPrefix + "help)");
                }
                commands.get(command).execute(args, event);
            }
            catch (BadUserInputException | BadStateException | IncorrectPermissionsException e) {
                event.getChannel().sendMessage(e.getMessage()).queue();
            }
            catch (Exception e) {
                // Log unexpected errors
                e.printStackTrace();
                Logger.logEvent(event.getMessage().getContentRaw(), e);
            }
        }

        /**
         * Displays a welcome message when a new member joins the server
         *
         * TODO Implement this doesn't activate when a new member joins
         */
        @Override
        public void onGuildMemberJoin(GuildMemberJoinEvent event) {
            super.onGuildMemberJoin(event);

            final List<TextChannel> channels = event.getGuild().getTextChannelsByName("general", false);
            if (channels.size() == 1) {
                final MessageChannel channel = channels.get(0);
                channel.sendMessage(String.format("Welcome @%s", event.getMember().getUser().getId()));
            }
        }
    }
}
