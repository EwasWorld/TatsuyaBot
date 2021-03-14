package CoreBox;

import BotFrameworkBox.Emoji;
import ExceptionsBox.BadStateException;
import ExceptionsBox.BadUserInputException;
import TatsuyaCommands.PomodoroCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static BotFrameworkBox.Bot.commandPrefix;

public class PomodoroSession {
    /*
     * Session Settings
     */
    public static final int maxDuration = 60 * 5;
    public static final int minDuration = 5;
    int workDuration = 25;
    int breakDuration = 10;
    Integer longBreakDuration = null;
    private Integer workSessionsBeforeLongBreak = null;
    private int timeoutDuration = 60;
    /**
     * Maps participants who wish to be pinged to a message of what they're doing
     */
    private final ParticipantInfo participantInfo = new ParticipantInfo();
    List<BooleanSetting> booleanSettings = null;

    /*
     * Fixed Info
     */
    private final Member author;
    private final MessageChannel channel;
    private Instant timeSessionStarted = null;

    /*
     * State information
     */
    private Instant timeCurrentStateStarted = null;
    private SessionState sessionState = SessionState.NOT_STARTED;
    /**
     * If the current state is 'paused', resume to this state
     */
    private SessionState resumeState = null;
    private Instant nextPing = null;
    private Message mainMessage = null;
    private Message pingMessage = null;
    private final HistoricStateData historicStateData = new HistoricStateData();

    public PomodoroSession(Member author, MessageChannel channel, String args, Instant currentTime) {
        this.author = author;
        this.channel = channel;
        setFromArgs(args);
        addParticipant(author, true);
        channel.sendMessage(buildEmbed(currentTime)).queue(createdMessage -> {
            mainMessage = createdMessage;
            updateMessageEmojis();
        });
    }

    public void addParticipant(Member participant, boolean ping, String studying) {
        participantInfo.addParticipant(participant, ping, studying);
    }

    public void addParticipant(Member participant, boolean ping) {
        participantInfo.addParticipant(participant, ping);
    }

    public void setMessage(Message message) {
        this.mainMessage = message;
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public String getMessageId() {
        return mainMessage.getId();
    }

    public String getChannelId() {
        return channel.getId();
    }

    public void setWorkDuration(int workDuration) {
        checkRange(workDuration);
        this.workDuration = workDuration;
    }

    private void checkRange(Integer duration) {
        if (duration == null) {
            return;
        }
        if (duration > maxDuration) {
            throw new BadUserInputException("Maximum duration: " + minutesToTimeString(maxDuration));
        }
        if (duration < minDuration) {
            throw new BadUserInputException("Minimum duration: " + minutesToTimeString(minDuration));
        }
    }

    public void setBreakDuration(int breakDuration) {
        checkRange(breakDuration);
        this.breakDuration = breakDuration;
    }

    public void setLongBreak(Integer workSessionsBeforeLongBreak, Integer longBreakDuration) {
        if (workSessionsBeforeLongBreak != null && longBreakDuration == null
                || workSessionsBeforeLongBreak == null && longBreakDuration != null) {
            throw new BadUserInputException("Must provide long break duration AND work sessions until long break (or neither)");
        }
        if (workSessionsBeforeLongBreak != null) {
            int min = 1;
            if (workSessionsBeforeLongBreak < min) {
                throw new BadUserInputException("Minimum " + min + " work session before long break");
            }
            int max = 30;
            if (workSessionsBeforeLongBreak > max) {
                throw new BadUserInputException("Maximum " + max + " work session before long break");
            }
        }
        checkRange(longBreakDuration);
        this.workSessionsBeforeLongBreak = workSessionsBeforeLongBreak;
        this.longBreakDuration = longBreakDuration;
    }

    public void setBooleanSetting(BooleanSetting setting, boolean value) {
        if (value) {
            this.booleanSettings.add(setting);
        }
        else {
            this.booleanSettings.remove(setting);
        }
    }

    public void setBooleanSettings(Map<BooleanSetting, Boolean> settings) {
        for (Map.Entry<BooleanSetting, Boolean> setting : settings.entrySet()) {
            setBooleanSetting(setting.getKey(), setting.getValue());
        }
    }

    public void removeParticipant(Member participant) {
        participantInfo.removeParticipant(participant);
    }

    public MessageEmbed buildEmbed(Instant timeNow) {
        int timeInCurrentState = 0;
        if (timeCurrentStateStarted != null) {
            timeInCurrentState = minutesBetweenTwoTimes(timeCurrentStateStarted, timeNow);
        }
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("Pomodoro Timer - %s", sessionState.stateTitle));
        embedBuilder.setDescription(currentCycleInfoString(timeNow));
        embedBuilder.addField("Ping party" + (!booleanSettings.contains(BooleanSetting.PINGS) ? " (off)" : ""), participantInfo.getMemberList(), true);
        embedBuilder.addField("People are working on", participantInfo.getWorkingOnList(), true);
        embedBuilder.addField("", "", true);
        embedBuilder.addField("Completed Stats", getSessionStartedTime() + "\n" + historicStateData.getCompletedStatsString(timeInCurrentState, sessionState), true);
        embedBuilder.addField("Session Settings", getSessionSettingsString(true), true);
        embedBuilder.setFooter(String.format("%shelp", commandPrefix));
        if (sessionState.defaultColour != null) {
            embedBuilder.setColor(sessionState.defaultColour);
        }
        if (sessionState.defaultThumbnail != null && booleanSettings.contains(BooleanSetting.IMAGES)) {
            embedBuilder.setImage(sessionState.defaultThumbnail);
        }
        return embedBuilder.build();
    }

    private String getSessionStartedTime() {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern((booleanSettings.contains(BooleanSetting.DATE) ? "yy/MM/dd " : "") +"HH:mm z");
        String string = "Started: ";
        if (timeSessionStarted == null) {
            return string + "--:--";
        }
        return string + ZonedDateTime.ofInstant(timeSessionStarted, ZoneId.systemDefault()).format(sdf);
    }

    private int minutesBetweenTwoTimes(Instant timeA, Instant timeB) {
        long milliDifference = Math.abs(timeA.toEpochMilli() - timeB.toEpochMilli());
        long conversion = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
        double diff = Math.ceil((double) milliDifference / conversion);
        if (diff > Integer.MAX_VALUE) {
            throw new BadStateException("Time difference too large");
        }
        return (int) diff;
    }

    private String currentCycleInfoString(Instant timeNow) {
        if (sessionState == SessionState.PAUSED) {
            return "Session is paused. Resume to continue " + resumeState.stateGeneral;
        }
        if (!sessionState.isStarted) {
            return "Timer not started";
        }
        String string = "";
        SessionState nextState = getNextState();
        string += String.format("%s until %s", minutesToTimeString(minutesBetweenTwoTimes(nextPing, timeNow)), nextState.stateGeneral);
        if (workSessionsBeforeLongBreak != null && nextState != SessionState.LONG_BREAK) {
            string += String.format("\n%d work sessions until long break (not including this one)", workSessionsBeforeLongBreak - historicStateData.workSessionsSinceLastLongBreak() - 1);
        }
        return string;
    }

    public String getSessionSettingsString(boolean shortVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Work: %s, Break: %s", minutesToTimeString(workDuration), minutesToTimeString(breakDuration)));
        if (workSessionsBeforeLongBreak != null) {
            sb.append(String.format("\nWork sessions before long break: %d, Long break: %s", workSessionsBeforeLongBreak, minutesToTimeString(longBreakDuration)));
        }
        else {
            sb.append("\nLong break not set");
        }
        sb.append("\nSession created by: ").append(author.getEffectiveName());
        if (!shortVersion) {
            sb.append("\n");
            boolean firstSetting = true;
            for (BooleanSetting setting : BooleanSetting.values()) {
                String formatting = "";
                if (!booleanSettings.contains(setting)) {
                    formatting = "~~";
                }
                if (!firstSetting) {
                    sb.append("・");
                } else {
                    firstSetting = false;
                }
                sb.append(formatting).append(setting.message).append(formatting);
            }
        }
        return sb.toString();
    }

    private SessionState getNextState() {
        if (sessionState != SessionState.WORK) {
            return SessionState.WORK;
        }
        if (workSessionsBeforeLongBreak != null && historicStateData.workSessionsSinceLastLongBreak() + 1 >= workSessionsBeforeLongBreak) {
            return SessionState.LONG_BREAK;
        }
        return SessionState.BREAK;
    }

    public static String minutesToTimeString(int minutes) {
        if (minutes == 0) {
            return "0 mins";
        }
        int hours = 0;
        if (minutes >= 60) {
            hours = Math.floorDiv(minutes, 60);
            minutes = minutes % 60;
        }
        StringBuilder sb = new StringBuilder();
        if (hours == 1) {
            sb.append("1 hour ");
        } else if (hours > 1) {
            sb.append(String.format("%d hours ", hours));
        }
        if (minutes == 1) {
            sb.append("1 min");
        } else if (minutes > 1) {
            sb.append(String.format("%d mins", minutes));
        }
        return sb.toString();
    }

    public void update(Instant currentTime, boolean forceNextState) {
        if ((nextPing != null && nextPing.isBefore(currentTime)) || forceNextState) {
            if (forceNextState && !sessionState.isStarted) {
                throw new BadUserInputException("Session is currently suspended, try starting it first");
            }
            SessionState nextState;
            if (!sessionState.isStarted) {
                nextState = SessionState.FINISHED;
            }
            else {
                nextState = getNextState();
            }
            boolean updateResumeState = true;
            if (!booleanSettings.contains(BooleanSetting.AUTO) && !forceNextState) {
                resumeState = nextState;
                nextState = SessionState.PAUSED;
                updateResumeState = false;
            }
            update(nextState, currentTime, updateResumeState);
        }
        else if (mainMessage != null) {
            mainMessage.editMessage(buildEmbed(currentTime)).queue();
        }
    }

    /**
     * @see #update(SessionState, Instant, boolean)
     */
    private void update(SessionState nextState, Instant currentTime) {
        update(nextState, currentTime, true);
    }

    /**
     * Update the current state and update the message
     */
    private void update(SessionState nextState, Instant currentTime, boolean updateResumeState) {
        /*
         * Update times
         */
        nextPing = currentTime.plus(historicStateData.getNextStateDuration(nextState), ChronoUnit.MINUTES);
        if (timeCurrentStateStarted != null) {
            historicStateData.addCompletedItem(minutesBetweenTwoTimes(timeCurrentStateStarted, currentTime), sessionState);
        }
        timeCurrentStateStarted = currentTime;

        /*
         * Update states
         */
        if (sessionState.isStarted && updateResumeState) {
            resumeState = sessionState;
        }
        sessionState = nextState;

        /*
         * Update messages and send pings
         */
        if (sessionState.isStarted || !updateResumeState) {
            Message oldPingMessage = pingMessage;
            channel.sendMessage(buildPingString()).queue(createdMessage -> pingMessage = createdMessage);
            Message oldMainMessage = mainMessage;
            channel.sendMessage(buildEmbed(currentTime)).queue(createdMessage -> {
                mainMessage = createdMessage;
                updateMessageEmojis();
            });
            if (booleanSettings.contains(BooleanSetting.DELETE)) {
                if (oldMainMessage != null) {
                    oldMainMessage.delete().queue();
                }
                if (oldPingMessage != null) {
                    oldPingMessage.delete().queue();
                }
            }
            return;
        }
        if (mainMessage != null) {
            mainMessage.editMessage(buildEmbed(currentTime)).queue(message -> updateMessageEmojis());
        }
    }

    private void updateMessageEmojis() {
        mainMessage.clearReactions().queue();
        for (Emoji emoji : PomodoroCommand.getAvailableEmojis(sessionState)) {
            emoji.addAsReaction(mainMessage);
        }
    }

    public void removeEmoji(String emote, User user) {
        mainMessage.removeReaction(emote, user).queue();
    }

    private String buildPingString() {
        StringBuilder sb = new StringBuilder(":clap: *Bangs Pots* :clap:");
        if (booleanSettings.contains(BooleanSetting.PINGS)) {
            String mentionString = participantInfo.getMentionString();
            if (mentionString != null && !mentionString.isBlank()) {
                sb.append("\n");
                sb.append(mentionString);
            }
        }
        sb.append(String.format("\nIt's %s time!", sessionState.stateGeneral));
        return sb.toString();
    }

    public void startSession(Instant currentTime) {
        if (sessionState != SessionState.NOT_STARTED) {
            throw new BadUserInputException("Session is already started");
        }
        timeSessionStarted = currentTime;
        update(SessionState.WORK, currentTime);
    }

    public void pauseSession(Instant currentTime) {
        if (sessionState == SessionState.NOT_STARTED) {
            throw new BadUserInputException("Session not started");
        }
        if (!sessionState.isStarted) {
            throw new BadUserInputException("Session is already suspended");
        }
        update(SessionState.PAUSED, currentTime);
    }

    public void resumeSession(Instant currentTime) {
        if (sessionState != SessionState.PAUSED) {
            throw new BadUserInputException("Session isn't paused so cannot resume");
        }
        if (resumeState == null) {
            throw new BadStateException("Uh oh, I don't remember what we were doing... Sorry");
        }
        update(resumeState, currentTime);
    }

    public void stopSession(Instant currentTime) {
        if (sessionState == SessionState.FINISHED) {
            throw new BadUserInputException("Session is already stopped");
        }
        update(SessionState.FINISHED, currentTime);
    }

    public String getCurrentStateTimeLeftAsString(Instant currentTime) {
        if (nextPing == null) {
            if (sessionState == SessionState.NOT_STARTED) {
                throw new BadUserInputException("Session not started");
            }
            if (sessionState.isStarted) {
                throw new BadStateException("Uh oh, someone forgot to set the timer, bad Tatsuya");
            }
            throw new BadUserInputException("Session is currently suspended");
        }
        return minutesToTimeString(minutesBetweenTwoTimes(currentTime, nextPing)) + " until " + sessionState.stateGeneral;
    }

    public void resetTime(Instant currentTime) {
        if (!sessionState.isStarted) {
            throw new BadUserInputException("Session is currently suspended");
        }

        int duration;
        switch (sessionState) {
            case WORK:
                duration = workDuration;
                break;
            case BREAK:
                duration = breakDuration;
                break;
            case LONG_BREAK:
                duration = longBreakDuration;
                break;
            default:
                throw new BadStateException("Oh, I don't know how long I'm supposed to make it...");
        }
        nextPing = currentTime.plus(duration, ChronoUnit.MINUTES);
        if (mainMessage != null) {
            mainMessage.editMessage(buildEmbed(currentTime)).queue();
        }
    }

    public void addTime(int minutes, Instant currentTime) {
        if (!sessionState.isStarted) {
            throw new BadUserInputException("Session is currently suspended");
        }
        if (minutes <= 0) {
            throw new BadUserInputException("Please enter a number of minutes greater than 0");
        }
        if (minutes >= maxDuration) {
            throw new BadUserInputException("Please enter a number of minutes less than than " + maxDuration);
        }
        nextPing = nextPing.plus(minutes, ChronoUnit.MINUTES);
        update(currentTime, false);
    }

    public void removeTime(int minutes, Instant currentTime) {
        if (!sessionState.isStarted) {
            throw new BadUserInputException("Session is currently suspended");
        }
        if (minutes <= 0) {
            throw new BadUserInputException("Please enter a number of minutes greater than 0");
        }
        if (minutes >= maxDuration) {
            throw new BadUserInputException("Please enter a number of minutes less than than " + maxDuration);
        }
        int timeDiff = minutesBetweenTwoTimes(currentTime, nextPing);
        if (timeDiff < minutes + 1) {
            throw new BadUserInputException("There's only " + minutesToTimeString(timeDiff) + " left!");
        }
        nextPing = nextPing.minus(minutes, ChronoUnit.MINUTES);
        update(currentTime, false);
    }

    public void setFromSettings(PomodoroSettings args) {
        if (args == null) {
            return;
        }

        // TODO
    }

    public void setFromArgs(String args) {
        Map<BooleanSetting, Boolean> booleanSettings = null;
        if (!args.isEmpty()) {
            List<Integer> argsInts = new ArrayList<>();
            for (String arg : args.split(" ")) {
                try {
                    argsInts.add(Integer.parseInt(arg));
                } catch (NumberFormatException ignored) {
                    String[] currentArg = arg.split(":");
                    if (currentArg.length != 2 || (!currentArg[1].equalsIgnoreCase("on") && !currentArg[1].equalsIgnoreCase("off"))) {
                        throw new BadUserInputException("Non-numerical arguments must be in the format 'ping:on' or 'ping:off'");
                    }
                    BooleanSetting setting;
                    try {
                        setting = BooleanSetting.valueOf(currentArg[0].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new BadUserInputException("Unknown setting: " + currentArg[0]);
                    }
                    if (booleanSettings == null) {
                        booleanSettings = new HashMap<>();
                    }
                    booleanSettings.put(setting, currentArg[1].equalsIgnoreCase("on"));
                }
            }
            if (argsInts.size() > 4) {
                throw new BadUserInputException("Arguments incorrect - too many numerical arguments");
            }
            Integer workDuration = null;
            Integer breakDuration = null;
            Integer longBreakDuration = null;
            Integer sessionsBeforeLongBreakDuration = null;
            if (argsInts.size() > 0) {
                workDuration = argsInts.get(0);
                checkRange(workDuration);
            }
            if (argsInts.size() > 1) {
                breakDuration = argsInts.get(1);
                checkRange(breakDuration);
            }
            if (argsInts.size() > 2) {
                longBreakDuration = argsInts.get(2);
                checkRange(longBreakDuration);
            }
            if (argsInts.size() > 3) {
                sessionsBeforeLongBreakDuration = argsInts.get(3);
            }

            /*
             * Set settings
             */
            setLongBreak(longBreakDuration, sessionsBeforeLongBreakDuration);
            if (workDuration != null) {
                setWorkDuration(workDuration);
            }
            if (breakDuration != null) {
                setBreakDuration(breakDuration);
            }
            if (booleanSettings != null) {
                setBooleanSettings(booleanSettings);
            }
        }
    }

    public enum BooleanSetting {
        PINGS("(Pings)"), AUTO("(Auto) Continue"), DELETE("(Delete) old messages"), IMAGES("(Images)"), DATE("Show full (date)");

        String message;

        BooleanSetting(String message) {
            this.message = message;
        }
    }

    public enum SessionState {
        WORK("WORKING", "work", true, Color.BLUE, "https://img.jakpost.net/c/2020/03/01/2020_03_01_87874_1583031914.jpg"),
        BREAK("BREAK", "break", true, Color.CYAN, "https://img.webmd.com/dtmcms/live/webmd/consumer_assets/site_images/article_thumbnails/slideshows/stretches_to_help_you_get_loose_slideshow/1800x1200_stretches_to_help_you_get_loose_slideshow.jpg"),
        LONG_BREAK("LONG BREAK", "long break", true, Color.CYAN, "https://miro.medium.com/max/10000/1*BbmQbf-ZHVIgBaoUVShq6g.jpeg"),
        NOT_STARTED("NOT STARTED", "", false, Color.ORANGE, "https://wp-media.labs.com/wp-content/uploads/2019/01/01140607/How-to-De-Clutter-Your-Workspace1.jpg"),
        PAUSED("PAUSED", "paused", false, Color.ORANGE, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT-zcKdGFYy2oPkxzqj0lXhGYDyLofR-c083Q&usqp=CAU"),
        FINISHED("FINISHED", "finished", false, null, "https://static01.nyt.com/images/2015/11/03/health/well_lyingdown/well_lyingdown-tmagArticle.jpg");

        String stateTitle;
        String stateGeneral;
        Color defaultColour;
        String defaultThumbnail;
        boolean isStarted;

        SessionState(String stateTitle, String stateGeneral, boolean isStarted, Color defaultColour, String defaultThumbnail) {
            this.stateTitle = stateTitle;
            this.stateGeneral = stateGeneral;
            this.defaultColour = defaultColour;
            this.defaultThumbnail = defaultThumbnail;
            this.isStarted = isStarted;
        }
    }

    private class HistoricStateData {
        private final List<DataItem> completedItems = new ArrayList<>();

        void addCompletedItem(int time, SessionState state) {
            if (state == SessionState.NOT_STARTED || state == SessionState.FINISHED) {
                time = 0;
            }
            completedItems.add(new DataItem(time, state));
        }

        int workSessionsSinceLastLongBreak() {
            int count = 0;
            for (int i = completedItems.size() - 1; i >= 0; i--) {
                DataItem item = completedItems.get(i);
                if (item.state == SessionState.LONG_BREAK) {
                    break;
                }
                else if (item.state == SessionState.WORK) {
                    count++;
                }
            }
            return count;
        }

        private String getCompletedStatsString(int timeInCurrentState, SessionState currentState) {
            int workSessionsCompleted = 0;
            int workTimeElapsed = 0;
            boolean workSession = false;
            for (DataItem dataItem : completedItems) {
                if (dataItem.state == SessionState.WORK) {
                    if (!workSession) {
                        workSessionsCompleted++;
                    }
                    workTimeElapsed += dataItem.time;
                    workSession = true;
                }
                else if (dataItem.state == SessionState.BREAK || dataItem.state == SessionState.LONG_BREAK) {
                    workSession = false;
                }
            }
            if (currentState == SessionState.WORK) {
                workTimeElapsed += timeInCurrentState;
            }

            String string = "Completed work sessions: " + workSessionsCompleted;
            string += "\nTotal study time: " + minutesToTimeString(workTimeElapsed);
            return string;
        }

        private int getNextStateDuration(SessionState nextState) {
            if (!nextState.isStarted) {
                return timeoutDuration;
            }
            int timeRemaining;
            switch (nextState) {
                case WORK:
                    timeRemaining = workDuration;
                    break;
                case BREAK:
                    timeRemaining = breakDuration;
                    break;
                case LONG_BREAK:
                    timeRemaining = longBreakDuration;
                    break;
                default:
                    throw new BadStateException("I've gotten myself in a pickle, can't calculate how long the next session should be");
            }
            for (int i = completedItems.size() - 1; i >= 0; i--) {
                DataItem item = completedItems.get(i);
                if (item.state == nextState) {
                    timeRemaining -= item.time;
                    continue;
                }
                if (!item.state.isStarted) {
                    continue;
                }
                break;
            }
            return timeRemaining;
        }

        class DataItem {
            int time;
            SessionState state;

            public DataItem(int time, SessionState state) {
                this.time = time;
                this.state = state;
            }
        }
    }

    private static class ParticipantInfo {
        private final Map<Member, ParticipantDetail> participants = new HashMap<>();

        public void removeParticipant(Member participant) {
            participants.remove(participant);
        }

        public void addParticipant(Member participant, boolean ping, String studying) {
            participants.put(participant, new ParticipantDetail(ping, studying));
        }

        public void addParticipant(Member participant, boolean ping) {
            participants.put(participant, new ParticipantDetail(ping));
        }

        private static class ParticipantDetail {
            boolean ping;
            String message = null;

            public ParticipantDetail(boolean ping, String message) {
                this.ping = ping;
                this.message = message;
            }

            public ParticipantDetail(boolean ping) {
                this.ping = ping;
            }
        }

        private String getMemberList() {
            if (participants.isEmpty()) {
                return "No one yet";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Member, ParticipantDetail> entry : participants.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(entry.getKey().getEffectiveName());
                if (!entry.getValue().ping) {
                    sb.append(" ");
                    sb.append(Emoji.ZIPPER_MOUTH.getDiscordAlias());
                }
            }
            return sb.toString();
        }

        private String getWorkingOnList() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Member, ParticipantDetail> entry : participants.entrySet()) {
                String workingOn = entry.getValue().message;

                if (workingOn != null && !workingOn.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(workingOn);
                }
            }
            if (sb.length() == 0) {
                return "Nothing submitted";
            }
            return sb.toString();
        }

        private String getMentionString() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Member, ParticipantDetail> entry : participants.entrySet()) {
                if (entry.getValue().ping) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append(entry.getKey().getAsMention());
                }
            }
            return sb.toString();
        }
    }
}
