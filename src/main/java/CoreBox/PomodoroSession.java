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

/**
 * Information about an active pomodoro session
 */
public class PomodoroSession {
    /*
     * Session Settings
     */
    /**
     * Maps participants who wish to be pinged to a message of what they're doing
     */
    private final Participants participants = new Participants();
    private final PomodoroSettings settings = new PomodoroSettings();

    /*
     * Fixed Info
     */
    private final Member author;
    private final MessageChannel channel;
    private Instant timeSessionStarted = null;

    /*
     * Current state information
     */
    private Instant timeCurrentStateStarted = null;
    private SessionState sessionState = SessionState.NOT_STARTED;
    /**
     * If the current state is 'paused', resume to this state
     */
    private SessionState resumeState = null;
    private Instant timeCurrentStateEnds = null;
    /**
     * The message containing the current timer info
     */
    private Message mainMessage = null;
    /**
     * The last ping message (kept so that it can be deleted next time a ping message is sent)
     */
    private Message pingMessage = null;
    private final HistoricStateData historicStateData = new HistoricStateData();

    public PomodoroSession(Member author, MessageChannel channel, String args, Instant currentTime) {
        this.author = author;
        this.channel = channel;
        settings.setFromArgs(args);
        participants.addParticipant(author, true);
        channel.sendMessage(buildEmbed(currentTime)).queue(createdMessage -> {
            mainMessage = createdMessage;
            updateMessageEmojis();
        });
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

    public PomodoroSettings getSettings() {
        return settings;
    }

    public Participants getParticipants() {
        return participants;
    }

    /**
     * @return the embed that will act as the {@link #mainMessage}
     */
    public MessageEmbed buildEmbed(Instant timeNow) {
        int timeInCurrentState = 0;
        if (timeCurrentStateStarted != null) {
            timeInCurrentState = minutesBetweenTwoTimes(timeCurrentStateStarted, timeNow);
        }
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("Pomodoro Timer - %s", sessionState.stateTitle));
        embedBuilder.setDescription(getCurrentStateString(timeNow));
        embedBuilder.addField("Ping party" + (!settings.getBooleanSetting(BooleanSetting.PINGS) ? " (off)" : ""), participants.getParticipantList(), true);
        embedBuilder.addField("People are working on", participants.getWorkingOnList(), true);
        // Blank field to fill last column (inline fields are in a 3-wide grid)
        embedBuilder.addField("", "", true);
        embedBuilder.addField("Completed Stats", getSessionStartTimeString() + "\n" + historicStateData.getCompletedStatsString(timeInCurrentState, sessionState), true);
        embedBuilder.addField("Session Settings", getSessionSettingsString(true), true);
        embedBuilder.setFooter(String.format("%shelp", commandPrefix));
        if (sessionState.defaultColour != null) {
            embedBuilder.setColor(sessionState.defaultColour);
        }
        if (sessionState.defaultImage != null && settings.getBooleanSetting(BooleanSetting.IMAGES)) {
            embedBuilder.setImage(settings.getStateImage(sessionState));
        }
        return embedBuilder.build();
    }

    private String getSessionStartTimeString() {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern((settings.getBooleanSetting(BooleanSetting.DATE) ? "yy/MM/dd " : "") + "HH:mm z");
        String string = "Started: ";
        if (timeSessionStarted == null) {
            return string + "--:--";
        }
        return string + ZonedDateTime.ofInstant(timeSessionStarted, ZoneId.systemDefault()).format(sdf);
    }

    private int minutesBetweenTwoTimes(Instant timeA, Instant timeB) {
        if (timeA == null || timeB == null) {
            throw new BadStateException("Instant cannot be null");
        }
        long milliDifference = Math.abs(timeA.toEpochMilli() - timeB.toEpochMilli());
        long conversion = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
        double diff = Math.ceil((double) milliDifference / conversion);
        if (diff > Integer.MAX_VALUE) {
            throw new BadStateException("Time difference too large");
        }
        return (int) diff;
    }

    /**
     * @return information about the current session state and, if appropriate, when the next state will begin
     */
    private String getCurrentStateString(Instant timeNow) {
        if (sessionState == SessionState.PAUSED) {
            return "Session is paused. Resume to continue " + resumeState.stateDisplayTitle;
        }
        if (sessionState == SessionState.FINISHED) {
            return "Session completed";
        }
        if (sessionState == SessionState.NOT_STARTED) {
            return "Timer not started";
        }
        if (!sessionState.isActiveState) {
            return "Session is currently suspended";
        }

        SessionState nextState = getNextState();
        Integer workSessionsBeforeLongBreak = settings.getWorkSessionsBeforeLongBreak();
        String returnString = String.format("%s until %s", minutesToDisplayString(minutesBetweenTwoTimes(timeCurrentStateEnds, timeNow)), nextState.stateDisplayTitle);
        if (workSessionsBeforeLongBreak != null && nextState != SessionState.LONG_BREAK) {
            returnString += String.format("\n%d work sessions until long break (not including this one)", workSessionsBeforeLongBreak - historicStateData.countWorkSessions(true) - 1);
        }
        return returnString;
    }

    /**
     * @param shortVersion whether to show boolean settings or not
     * @return string displaying timings, author, and boolean settings
     */
    public String getSessionSettingsString(boolean shortVersion) {
        StringBuilder sb = new StringBuilder();
        /*
         * Timings
         */
        sb.append(String.format("Work: %s, Break: %s", minutesToDisplayString(settings.getStateDuration(SessionState.WORK)), minutesToDisplayString(settings.getStateDuration(SessionState.WORK))));
        Integer workSessionsBeforeLongBreak = settings.getWorkSessionsBeforeLongBreak();
        if (workSessionsBeforeLongBreak != null) {
            sb.append(String.format("\nWork sessions before long break: %d, Long break: %s", workSessionsBeforeLongBreak, minutesToDisplayString(settings.getStateDuration(SessionState.LONG_BREAK))));
        }
        else {
            sb.append("\nLong break not set");
        }

        /*
         * Author
         */
        sb.append("\nSession created by: ").append(author.getEffectiveName());

        if (!shortVersion) {
            /*
             * Boolean settings (strike through settings that are off)
             */
            sb.append("\n");
            boolean firstSetting = true;
            for (BooleanSetting setting : BooleanSetting.values()) {
                String formatting = "";
                if (!settings.getBooleanSetting(setting)) {
                    formatting = "~~";
                }
                if (!firstSetting) {
                    sb.append("・");
                }
                else {
                    firstSetting = false;
                }
                sb.append(formatting).append(setting.displayMessage).append(formatting);
            }
        }
        return sb.toString();
    }

    /**
     * @return work, break, or long break
     */
    private SessionState getNextState() {
        if (sessionState != SessionState.WORK) {
            return SessionState.WORK;
        }
        Integer workSessionsBeforeLongBreak = settings.getWorkSessionsBeforeLongBreak();
        if (workSessionsBeforeLongBreak != null && historicStateData.countWorkSessions(true) + 1 >= workSessionsBeforeLongBreak) {
            return SessionState.LONG_BREAK;
        }
        return SessionState.BREAK;
    }

    /**
     * @return "2 hours 3 mins", pluralising as necessary and omitting hours if not required
     */
    public static String minutesToDisplayString(int minutes) {
        if (minutes < 0) {
            throw new BadStateException("Cannot display a time of less than 0 minutes");
        }
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
        }
        else if (hours > 1) {
            sb.append(String.format("%d hours ", hours));
        }
        if (minutes == 1) {
            sb.append("1 min");
        }
        else if (minutes > 1) {
            sb.append(String.format("%d mins", minutes));
        }
        return sb.toString();
    }

    /**
     * Refreshes the {@link #mainMessage}, moving to the next state if the appropriate time has elapsed
     *
     * @param forceNextState forcefully move to the next state whether the appropriate time has elapsed or not
     * @throws BadUserInputException if session is suspended and forceNextState is true
     */
    public void update(Instant currentTime, boolean forceNextState) {
        if (forceNextState || (timeCurrentStateEnds != null && timeCurrentStateEnds.isBefore(currentTime))) {
            if (forceNextState && !sessionState.isActiveState) {
                throw new BadUserInputException("Session is currently suspended, try starting it first");
            }
            SessionState nextState;
            if (!sessionState.isActiveState) {
                // Time out
                nextState = SessionState.FINISHED;
            }
            else {
                nextState = getNextState();
            }
            boolean forceSendPings = false;
            if (!settings.getBooleanSetting(BooleanSetting.AUTO) && !forceNextState) {
                resumeState = nextState;
                nextState = SessionState.PAUSED;
                forceSendPings = true;
            }
            update(nextState, currentTime, forceSendPings);
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
     * Update the current state, update the message, ping if moving to an active state
     *
     * @param forceSendPings ping even if not moving to an active state
     */
    private void update(SessionState nextState, Instant currentTime, boolean forceSendPings) {
        /*
         * Update times
         */
        timeCurrentStateEnds = currentTime.plus(historicStateData.getNextStateDurationAdjusted(nextState), ChronoUnit.MINUTES);
        if (timeCurrentStateStarted != null) {
            historicStateData.addCompletedItem(minutesBetweenTwoTimes(timeCurrentStateStarted, currentTime), sessionState);
        }
        timeCurrentStateStarted = currentTime;

        /*
         * Update states
         */
        if (sessionState.isActiveState) {
            resumeState = sessionState;
        }
        sessionState = nextState;

        /*
         * Update messages and send pings
         */
        if (sessionState.isActiveState || forceSendPings) {
            /*
             * Build messages
             */
            StringBuilder pingString = new StringBuilder(":clap: *Bangs Pots* :clap:");
            if (settings.getBooleanSetting(BooleanSetting.PINGS)) {
                String mentionString = participants.getMentionList();
                if (mentionString != null && !mentionString.isBlank()) {
                    pingString.append("\n");
                    pingString.append(mentionString);
                }
            }
            pingString.append(String.format("\nIt's %s time!", sessionState.stateDisplayTitle));

            /*
             * Send messages
             */
            Message oldPingMessage = pingMessage;
            channel.sendMessage(pingString.toString()).queue(createdMessage -> pingMessage = createdMessage);
            Message oldMainMessage = mainMessage;
            channel.sendMessage(buildEmbed(currentTime)).queue(createdMessage -> {
                mainMessage = createdMessage;
                updateMessageEmojis();
            });

            /*
             * Clean up
             */
            if (settings.getBooleanSetting(BooleanSetting.DELETE)) {
                if (oldMainMessage != null) {
                    oldMainMessage.delete().queue();
                }
                if (oldPingMessage != null) {
                    oldPingMessage.delete().queue();
                }
            }
        }
        else if (mainMessage != null) {
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

    /**
     * Action triggered by user
     */
    public void userStartSession(Instant currentTime) {
        if (sessionState != SessionState.NOT_STARTED) {
            throw new BadUserInputException("Session is already started");
        }
        timeSessionStarted = currentTime;
        update(SessionState.WORK, currentTime);
    }

    /**
     * Action triggered by user
     */
    public void userPauseSession(Instant currentTime) {
        if (sessionState == SessionState.NOT_STARTED) {
            throw new BadUserInputException("Session not started");
        }
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is already suspended");
        }
        update(SessionState.PAUSED, currentTime);
    }

    /**
     * Action triggered by user
     */
    public void userResumeSession(Instant currentTime) {
        if (sessionState != SessionState.PAUSED) {
            throw new BadUserInputException("Session isn't paused so cannot resume");
        }
        if (resumeState == null) {
            throw new BadStateException("Uh oh, I don't remember what we were doing... Sorry");
        }
        update(resumeState, currentTime);
    }

    /**
     * Action triggered by user
     */
    public void userStopSession(Instant currentTime) {
        if (sessionState == SessionState.FINISHED) {
            throw new BadUserInputException("Session is already stopped");
        }
        update(SessionState.FINISHED, currentTime);
    }

    public String getCurrentStateTimeLeftAsString(Instant currentTime) {
        if (timeCurrentStateEnds == null) {
            if (sessionState == SessionState.NOT_STARTED) {
                throw new BadUserInputException("Session not started");
            }
            if (sessionState.isActiveState) {
                throw new BadStateException("Uh oh, someone forgot to set the timer, bad Tatsuya");
            }
            throw new BadUserInputException("Session is currently suspended");
        }
        return minutesToDisplayString(minutesBetweenTwoTimes(currentTime, timeCurrentStateEnds)) + " until " + sessionState.stateDisplayTitle;
    }

    public void resetTimeOnCurrentState(Instant currentTime) {
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is currently suspended");
        }

        timeCurrentStateEnds = currentTime.plus(settings.getStateDuration(sessionState), ChronoUnit.MINUTES);
        if (mainMessage != null) {
            mainMessage.editMessage(buildEmbed(currentTime)).queue();
        }
    }

    public void addTimeToCurrentState(int minutes, Instant currentTime) {
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is currently suspended");
        }
        if (minutes <= 0) {
            throw new BadUserInputException("Please enter a number of minutes greater than 0");
        }
        int timeRemaining = minutesBetweenTwoTimes(currentTime, timeCurrentStateEnds);
        if (timeRemaining + minutes >= PomodoroSettings.maxDuration) {
            throw new BadUserInputException("Please enter a number of minutes less than than " + (PomodoroSettings.maxDuration - timeRemaining));
        }
        timeCurrentStateEnds = timeCurrentStateEnds.plus(minutes, ChronoUnit.MINUTES);
        update(currentTime, false);
    }

    public void removeTimeFromCurrentState(int minutes, Instant currentTime) {
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is currently suspended");
        }
        if (minutes <= 0) {
            throw new BadUserInputException("Please enter a number of minutes greater than 0");
        }
        int timeDiff = minutesBetweenTwoTimes(currentTime, timeCurrentStateEnds);
        if (timeDiff < minutes + 1) {
            throw new BadUserInputException("There's only " + minutesToDisplayString(timeDiff) + " left! Can lower it by a maximum of " + minutesToDisplayString(timeDiff - 1));
        }
        timeCurrentStateEnds = timeCurrentStateEnds.minus(minutes, ChronoUnit.MINUTES);
        update(currentTime, false);
    }

    /**
     * Settings which can either be on or off
     */
    public enum BooleanSetting {
        PINGS("(Pings)"), AUTO("(Auto) Continue"), DELETE("(Delete) old messages"), IMAGES("(Images)"), DATE("Show full (date)");

        /**
         * How this can be displayed. Ideally, the value should be bracketed so the users know what they need to input to get this setting to activate
         * e.g. AUTO might be displayed as "(Auto) Continue"
         */
        String displayMessage;

        BooleanSetting(String displayMessage) {
            this.displayMessage = displayMessage;
        }
    }

    public enum SessionState {
        WORK("WORKING", "work", true, Color.BLUE, "https://img.jakpost.net/c/2020/03/01/2020_03_01_87874_1583031914.jpg"),
        BREAK("BREAK", "break", true, Color.CYAN, "https://img.webmd.com/dtmcms/live/webmd/consumer_assets/site_images/article_thumbnails/slideshows/stretches_to_help_you_get_loose_slideshow/1800x1200_stretches_to_help_you_get_loose_slideshow.jpg"),
        LONG_BREAK("LONG BREAK", "long break", true, Color.CYAN, "https://miro.medium.com/max/10000/1*BbmQbf-ZHVIgBaoUVShq6g.jpeg"),
        NOT_STARTED("NOT STARTED", "", false, Color.ORANGE, "https://wp-media.labs.com/wp-content/uploads/2019/01/01140607/How-to-De-Clutter-Your-Workspace1.jpg"),
        PAUSED("PAUSED", "paused", false, Color.ORANGE, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT-zcKdGFYy2oPkxzqj0lXhGYDyLofR-c083Q&usqp=CAU"),
        FINISHED("FINISHED", "finished", false, null, "https://static01.nyt.com/images/2015/11/03/health/well_lyingdown/well_lyingdown-tmagArticle.jpg");

        /**
         * The message title when this is the state
         */
        String stateTitle;
        /**
         * E.g. "The next state is X"
         */
        String stateDisplayTitle;
        Color defaultColour;
        String defaultImage;
        /**
         * Active states are the standard pomodoro states of work/break/long break
         * Non-active states are suspended states like paused/not started/finished
         */
        boolean isActiveState;

        SessionState(String stateTitle, String stateDisplayTitle, boolean isActiveState, Color defaultColour, String defaultImage) {
            this.stateTitle = stateTitle;
            this.stateDisplayTitle = stateDisplayTitle.toLowerCase();
            this.defaultColour = defaultColour;
            this.defaultImage = defaultImage;
            this.isActiveState = isActiveState;
        }
    }

    /**
     * Information on previous states and their timings so that information such as total work time can be calculated
     */
    private class HistoricStateData {
        /**
         * Data is added to the end (later items are more recent)
         */
        private final List<DataItem> completedItems = new ArrayList<>();

        void addCompletedItem(int time, SessionState state) {
            if (state == SessionState.NOT_STARTED || state == SessionState.FINISHED) {
                time = 0;
            }
            completedItems.add(new DataItem(time, state));
        }

        /**
         * WORK PAUSE WORK counts as 1
         *
         * @param countSinceLongBreak false: count all work session, true: work sessions since last long break
         */
        int countWorkSessions(boolean countSinceLongBreak) {
            SessionState lastActiveState = null;
            int count = 0;
            for (int i = completedItems.size() - 1; i >= 0; i--) {
                DataItem item = completedItems.get(i);
                if (item.state == SessionState.LONG_BREAK && countSinceLongBreak) {
                    break;
                }
                else if (item.state == SessionState.WORK && lastActiveState != SessionState.WORK) {
                    count++;
                }
                if (item.state.isActiveState) {
                    lastActiveState = item.state;
                }
            }
            return count;
        }

        /**
         * @return a display string of completed work sessions and total study time
         */
        private String getCompletedStatsString(int timeInCurrentState, SessionState currentState) {
            int workTimeElapsed = 0;
            for (DataItem dataItem : completedItems) {
                if (dataItem.state == SessionState.WORK) {
                    workTimeElapsed += dataItem.time;
                }
            }
            if (currentState == SessionState.WORK) {
                workTimeElapsed += timeInCurrentState;
            }

            String returnString = "Completed work sessions: " + countWorkSessions(false);
            returnString += "\nTotal study time: " + minutesToDisplayString(workTimeElapsed);
            return returnString;
        }

        /**
         * @return the time the next state should be elapsed for (adjusts if for example a work session was paused and is to be resumed)
         */
        private int getNextStateDurationAdjusted(SessionState nextState) {
            int timeRemaining = settings.getStateDuration(nextState);
            if (!nextState.isActiveState) {
                return timeRemaining;
            }
            for (int i = completedItems.size() - 1; i >= 0; i--) {
                DataItem item = completedItems.get(i);
                if (item.state == nextState) {
                    timeRemaining -= item.time;
                    continue;
                }
                if (!item.state.isActiveState) {
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

    public static class Participants {
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
            String workingOn = null;

            public ParticipantDetail(boolean ping, String workingOn) {
                this.ping = ping;
                this.workingOn = workingOn;
            }

            public ParticipantDetail(boolean ping) {
                this.ping = ping;
            }
        }

        /**
         * @return a newline-separated list of participants
         */
        private String getParticipantList() {
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

        /**
         * @return a newline-separated list of what members are working on
         */
        private String getWorkingOnList() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Member, ParticipantDetail> entry : participants.entrySet()) {
                String workingOn = entry.getValue().workingOn;

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

        /**
         * @return a space-separated list of all members who want pings as pings
         */
        private String getMentionList() {
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
