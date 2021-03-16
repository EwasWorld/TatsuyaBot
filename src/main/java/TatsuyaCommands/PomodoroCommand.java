package TatsuyaCommands;

import BotFrameworkBox.*;
import CoreBox.PomodoroSession;
import CoreBox.PomodoroSession.SessionState;
import ExceptionsBox.BadStateException;
import ExceptionsBox.BadUserInputException;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static BotFrameworkBox.Bot.commandPrefix;

/**
 * TODO Guild default settings for colours/thumbnails/default timings/auto pings, etc/banned members (db with key as guild ID then json data?)
 * TODO Admin settings - is there anything on the Member object that distinguishes permissions? Manage server?
 * member.hasPermission(Permission.MANAGE_SERVER, Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL);
 * TODO Stop if message is deleted
 * TODO Override 'People are working on'. Let someone dictate on creation what's being worked on
 * TODO Alternative commands pomo/p
 * TODO Different arrangements (minimal, turn statuses off)
 * TODO Clear all 'working on's
 * TODO Clear all participants
 * TODO Clear stats
 * TODO Pre-ping party? 2 mins before end of current session
 * TODO Only count it towards work session count if >50% of the session was completed
 * TODO Only count time if >2mins have passed
 * TODO Setting: change time format of start time displayed
 */
public class PomodoroCommand extends AbstractCommand implements EmojiReactionCommand {
    public static String POMODORO_COMMAND = "pomodoro";
    private static final Map<String, PomodoroSession> sessionsByChannelId = new HashMap<>();
    private static final Set<String> bannedMembers = new HashSet<>();
    protected static final int defaultShortBump = 5;
    protected static final int defaultBigBump = 20;
    private static boolean updateThreadRunning = false;
    private static final Runnable updateRunnable = () -> {
        updateThreadRunning = true;
        Instant nextCheck = Instant.now();
        while (!sessionsByChannelId.isEmpty() && updateThreadRunning) {
            Instant currentTime = Instant.now();
            if (nextCheck.isAfter(currentTime)) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            Set<String> removeSessions = new HashSet<>();
            for (Map.Entry<String, PomodoroSession> session : sessionsByChannelId.entrySet()) {
                if (session.getValue().getSessionState() == SessionState.FINISHED) {
                    removeSessions.add(session.getKey());
                    continue;
                }
                session.getValue().update(currentTime, false);
                if (session.getValue().getSessionState() == SessionState.FINISHED) {
                    removeSessions.add(session.getKey());
                }
            }
            removeSessions.forEach(sessionsByChannelId::remove);

            nextCheck = nextCheck.plus(20, ChronoUnit.SECONDS);
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        updateThreadRunning = false;
    };
    private static final Map<Emoji, List<PomodoroSecondaryCommands>> emojiCommandMapping = getEmojiCommandMapping();

    private static Map<Emoji, List<PomodoroSecondaryCommands>> getEmojiCommandMapping() {
        Map<Emoji, List<PomodoroSecondaryCommands>> emojiCommandMapping = new HashMap<>();
        for (PomodoroSecondaryCommands command : PomodoroSecondaryCommands.values()) {
            Emoji emoji = command.getEmoji();
            if (emoji == null) {
                continue;
            }
            if (!emojiCommandMapping.containsKey(emoji)) {
                emojiCommandMapping.put(emoji, new ArrayList<>());
            }
            emojiCommandMapping.get(emoji).add(command);
        }
        return emojiCommandMapping;
    }

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
        executeSecondaryArgument(PomodoroSecondaryCommands.class, 1, args, event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return POMODORO_COMMAND;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Create a pomodoro timer";
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
        return super.getArguments();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CommandInterface[] getSecondaryCommands() {
        return PomodoroSecondaryCommands.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean executeFromAddReaction(GenericGuildMessageReactionEvent event) {
        PomodoroSession session;
        try {
            session = getSession(event.getChannel().getId());
        } catch (BadUserInputException e) {
            return false;
        }
        // Only look at emojis on a pomodoro message
        if (!session.getMessageId().equals(event.getMessageId())) {
            return false;
        }
        Optional<Emoji> emoji = Emoji.getFromMessageReaction(event.getReaction());
        // Check the emoji is recognised by this command
        if (emoji.isEmpty() || !emojiCommandMapping.containsKey(emoji.get())) {
            return true;
        }
        List<PomodoroSecondaryCommands> commands = emojiCommandMapping.get(emoji.get());
        if (commands.size() == 0) {
            throw new BadStateException("I've messed up my pomodoro emojis, oopsies");
        }
        PomodoroSecondaryCommands command = null;
        if (commands.size() == 1) {
            command = commands.get(0);
        }
        else {
            List<PomodoroSecondaryCommands> stateCommands = getAvailableActions(session.getSessionState());
            for (PomodoroSecondaryCommands stateCommand : stateCommands) {
                if (stateCommand.getEmoji() == emoji.get()) {
                    command = stateCommand;
                    break;
                }
            }
            if (command == null) {
                throw new BadStateException("Unexpected emoji reaction, someone's messed up...");
            }
        }
        try {
            command.emojiExecute(session, event.getMember());
        } catch (BadUserInputException e) {
            event.getChannel().sendMessage(e.getMessage()).queue();
        }
        if (command.removeEmoji()) {
            session.removeEmoji(event.getReactionEmote().getEmoji(), event.getUser());
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean executeFromRemoveReaction(GenericGuildMessageReactionEvent event) {
        return false;
    }

    private static List<PomodoroSecondaryCommands> getAvailableActions(SessionState state) {
        List<PomodoroSecondaryCommands> commands = new ArrayList<>() {
            {
                add(PomodoroSecondaryCommands.JOIN);
                add(PomodoroSecondaryCommands.LEAVE);
                add(PomodoroSecondaryCommands.STOP);
            }
        };
        switch (state) {
            case WORK:
            case BREAK:
            case LONG_BREAK:
                commands.add(PomodoroSecondaryCommands.PAUSE);
                commands.add(PomodoroSecondaryCommands.SKIP);
                commands.add(PomodoroSecondaryCommands.BUMP);
                commands.add(PomodoroSecondaryCommands.BIG_BUMP);
                commands.add(PomodoroSecondaryCommands.LOWER);
                commands.add(PomodoroSecondaryCommands.BIG_LOWER);
                break;
            case NOT_STARTED:
                commands.add(PomodoroSecondaryCommands.START);
                break;
            case PAUSED:
                commands.add(PomodoroSecondaryCommands.RESUME);
                break;
            case FINISHED:
                return new ArrayList<>();
        }
        return commands;
    }

    public static List<Emoji> getAvailableEmojis(SessionState state) {
        List<PomodoroSecondaryCommands> commands = getAvailableActions(state);
        commands.sort(Comparator.comparingInt(PomodoroSecondaryCommands::getEmojiPriority));

        List<Emoji> emojis = new ArrayList<>();
        for (PomodoroSecondaryCommands command : commands) {
            if (command.getEmoji() == null) {
                throw new BadStateException("Specified command doesn't have an emoji");
            }
            if (emojis.contains(command.getEmoji())) {
                throw new BadStateException("Ambiguous emoji");
            }
            emojis.add(command.getEmoji());
        }
        return emojis;
    }

    public String getArgumentFormat() {
        return PomodoroSecondaryCommands.NEW.getArguments();
    }

    private enum PomodoroSecondaryCommands implements CommandInterface {
        NEW {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                String channelId = event.getChannel().getId();
                if (sessionsByChannelId.containsKey(channelId)) {
                    throw new BadUserInputException("This channel already has a pomodoro session going on");
                }

                PomodoroSession session = new PomodoroSession(event.getMember(), event.getChannel(), args, Instant.now());
                sessionsByChannelId.put(channelId, session);
                if (!updateThreadRunning) {
                    new Thread(updateRunnable).start();
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Create a new pomodoro session";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "[work time] [break time] [{long break time} {work sessions before long break}] [pings:on] [auto:on] [delete:on] [images:on]";
            }
        },
        JOIN {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                boolean ping = true;
                String noPing = "noPing";
                if (args.startsWith(noPing)) {
                    args = args.substring(noPing.length()).trim();
                    ping = false;
                }

                boolean isBanned = bannedMembers.contains(event.getMember().getId());
                if (args.isEmpty() || isBanned) {
                    session.getParticipants().addParticipant(event.getMember(), ping);
                    if (!args.isEmpty()) {
                        sendMessage(event.getChannel(), event.getMember().getEffectiveName() + ", you've been banned from posting a status.");
                    }
                } else {
                    session.getParticipants().addParticipant(event.getMember(), ping, args);
                }
                session.update(Instant.now(), false);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Join the ping party and let everyone know what you're working on.";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "[noPing] [currently working on]";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.PERSON_HAND_RAISED;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 10;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.getParticipants().addParticipant(member, true);
                session.update(Instant.now(), false);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        LEAVE {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Leave the ping party, also removes your 'working on' text from the list";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.PERSON_NO_HANDS;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 12;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.getParticipants().removeParticipant(member);
                session.update(Instant.now(), false);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        EDIT {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                session.getSettings().setFromArgs(args);
                session.update(Instant.now(), false);
                sendMessage(event.getChannel(), "Settings updated");
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Update session settings";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return NEW.getArguments();
            }
        },
        TIME {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                sendMessage(event.getChannel(), session.getCurrentStateTimeLeftAsString(Instant.now()));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Gives the time left in the current state";
            }
        },
        START {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Start the timer";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.PLAY;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.userStartSession(Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 0;
            }
        },
        PAUSE {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Pause the session";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.PAUSE;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.userPauseSession(Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 5;
            }
        },
        RESUME {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Resume a paused session";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.PLAY;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.userResumeSession(Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 5;
            }
        },
        SKIP {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Skip to the next state";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.SKIP;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.update(Instant.now(), true);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 3;
            }
        },
        RESET {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Restart the timer for the the current state (e.g. restart the work timer if working)";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.RESET;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.resetTimeOnCurrentState(Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 19;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        BUMP {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                int time = defaultShortBump;
                if (!args.isBlank()) {
                    try {
                        time = Integer.parseInt(args);
                    } catch (NumberFormatException e) {
                        throw new BadUserInputException("Argument must be a number");
                    }
                }
                session.addTimeToCurrentState(time, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Increase the length of the current timer (default " + defaultShortBump + ")";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "[minutes]";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.UP_ARROW;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.addTimeToCurrentState(defaultShortBump, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 12;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        BIG_BUMP {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                int time = defaultBigBump;
                if (!args.isBlank()) {
                    try {
                        time = Integer.parseInt(args);
                    } catch (NumberFormatException e) {
                        throw new BadUserInputException("Argument must be a number");
                    }
                }
                session.addTimeToCurrentState(time, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Increase the length of the current timer (default " + defaultBigBump + ")";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "[minutes]";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.DOUBLE_UP_ARROW;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.addTimeToCurrentState(defaultBigBump, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 13;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        LOWER {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                int time = defaultShortBump;
                if (!args.isBlank()) {
                    try {
                        time = Integer.parseInt(args);
                    } catch (NumberFormatException e) {
                        throw new BadUserInputException("Argument must be a number");
                    }
                }
                session.removeTimeFromCurrentState(time, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Decrease the length of the current timer (default " + defaultShortBump + ")";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "[minutes]";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.DOWN_ARROW;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.removeTimeFromCurrentState(defaultShortBump, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 14;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        BIG_LOWER {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                int time = defaultBigBump;
                if (!args.isBlank()) {
                    try {
                        time = Integer.parseInt(args);
                    } catch (NumberFormatException e) {
                        throw new BadUserInputException("Argument must be a number");
                    }
                }
                session.removeTimeFromCurrentState(time, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Increase the length of the current timer (default " + defaultBigBump + ")";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "[minutes]";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.DOUBLE_DOWN_ARROW;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.removeTimeFromCurrentState(defaultBigBump, Instant.now());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 15;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeEmoji() {
                return true;
            }
        },
        STOP {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                emojiExecute(getSession(event.getChannel().getId()), event.getMember());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Ends the session";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Emoji getEmoji() {
                return Emoji.STOP;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void emojiExecute(PomodoroSession session, Member member) {
                session.userStopSession(Instant.now());
                sessionsByChannelId.remove(session.getChannelId());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getEmojiPriority() {
                return 20;
            }
        },
        SETTINGS {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                PomodoroSession session = getSession(event.getChannel().getId());
                sendMessage(event.getChannel(), session.getSessionSettingsString(false));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Get the current session settings";
            }
        },
        BAN {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                List<Member> mentions = event.getMessage().getMentionedMembers();
                if (mentions.isEmpty()) {
                    throw new BadUserInputException("No members mentioned");
                }
                for (Member member : mentions) {
                    bannedMembers.add(member.getId());
                }
                sendMessage(event.getChannel(), String.format("%s member%s banned from posting statuses", mentions.size(), mentions.size() != 1 ? "s" : ""));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Ban members from posting statuses";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Rank getRequiredRank() {
                return Rank.ADMIN;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "@bannedMember";
            }
        },
        UNBAN {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(@NotNull String args, @NotNull MessageReceivedEvent event) {
                List<Member> mentions = event.getMessage().getMentionedMembers();
                if (mentions.isEmpty()) {
                    throw new BadUserInputException("No members mentioned");
                }
                for (Member member : mentions) {
                    bannedMembers.remove(member.getId());
                }
                sendMessage(event.getChannel(), String.format("%s member%s unbanned from posting statuses", mentions.size(), mentions.size() != 1 ? "s" : ""));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDescription() {
                return "Unban members from posting statuses";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Rank getRequiredRank() {
                return Rank.ADMIN;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getArguments() {
                return "@bannedMember";
            }
        };

        /**
         * {@inheritDoc}
         */
        @Override
        public String getCommand() {
            return this.toString().toLowerCase().replaceAll("_", " ");
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
            return null;
        }

        public Emoji getEmoji() {
            return null;
        }

        public void emojiExecute(PomodoroSession session, Member member) {
            throw new BadStateException("Uh oh, I don't know what to do with this emoji");
        }

        public int getEmojiPriority() {
            throw new BadStateException("Uh oh, I don't know what to do with this emoji");
        }

        public boolean removeEmoji() {
            return false;
        }
    }

    private static PomodoroSession getSession(String channelId) {
        PomodoroSession session = sessionsByChannelId.get(channelId);
        if (session == null) {
            throw new BadUserInputException("There's no session in this channel, try " + commandPrefix + POMODORO_COMMAND + " " + PomodoroSecondaryCommands.NEW.getCommand());
        }
        return session;
    }
}
