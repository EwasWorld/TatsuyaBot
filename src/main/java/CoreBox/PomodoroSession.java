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
    /**
     * Maps participants who wish to be pinged to a message of what they're doing
     */
    private final ParticipantInfo participantInfo = new ParticipantInfo();
    private final PomodoroSettings settings = new PomodoroSettings();

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
        settings.setFromArgs(args);
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

    public PomodoroSettings getSettings() {
        return settings;
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
        embedBuilder.addField("Ping party" + (!settings.hasSetting(BooleanSetting.PINGS) ? " (off)" : ""), participantInfo.getMemberList(), true);
        embedBuilder.addField("People are working on", participantInfo.getWorkingOnList(), true);
        embedBuilder.addField("", "", true);
        embedBuilder.addField("Completed Stats", getSessionStartedTime() + "\n" + historicStateData.getCompletedStatsString(timeInCurrentState, sessionState), true);
        embedBuilder.addField("Session Settings", getSessionSettingsString(true), true);
        embedBuilder.setFooter(String.format("%shelp", commandPrefix));
        if (sessionState.defaultColour != null) {
            embedBuilder.setColor(sessionState.defaultColour);
        }
        if (sessionState.defaultThumbnail != null && settings.hasSetting(BooleanSetting.IMAGES)) {
            embedBuilder.setImage(sessionState.defaultThumbnail);
        }
        return embedBuilder.build();
    }

    private String getSessionStartedTime() {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern((settings.hasSetting(BooleanSetting.DATE) ? "yy/MM/dd " : "") +"HH:mm z");
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
        if (!sessionState.isActiveState) {
            return "Timer not started";
        }
        String string = "";
        SessionState nextState = getNextState();
        string += String.format("%s until %s", minutesToTimeString(minutesBetweenTwoTimes(nextPing, timeNow)), nextState.stateGeneral);
        Integer workSessionsBeforeLongBreak = settings.getWorkSessionsBeforeLongBreak();
        if (workSessionsBeforeLongBreak != null && nextState != SessionState.LONG_BREAK) {
            string += String.format("\n%d work sessions until long break (not including this one)", workSessionsBeforeLongBreak - historicStateData.workSessionsSinceLastLongBreak() - 1);
        }
        return string;
    }

    public String getSessionSettingsString(boolean shortVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Work: %s, Break: %s", minutesToTimeString(settings.getStateDuration(SessionState.WORK)), minutesToTimeString(settings.getStateDuration(SessionState.WORK))));
        Integer workSessionsBeforeLongBreak = settings.getWorkSessionsBeforeLongBreak();
        if (workSessionsBeforeLongBreak != null) {
            sb.append(String.format("\nWork sessions before long break: %d, Long break: %s", workSessionsBeforeLongBreak, minutesToTimeString(settings.getStateDuration(SessionState.LONG_BREAK))));
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
                if (!settings.hasSetting(setting)) {
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
        Integer workSessionsBeforeLongBreak = settings.getWorkSessionsBeforeLongBreak();
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
            if (forceNextState && !sessionState.isActiveState) {
                throw new BadUserInputException("Session is currently suspended, try starting it first");
            }
            SessionState nextState;
            if (!sessionState.isActiveState) {
                nextState = SessionState.FINISHED;
            }
            else {
                nextState = getNextState();
            }
            boolean updateResumeState = true;
            if (!settings.hasSetting(BooleanSetting.AUTO) && !forceNextState) {
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
        if (sessionState.isActiveState && updateResumeState) {
            resumeState = sessionState;
        }
        sessionState = nextState;

        /*
         * Update messages and send pings
         */
        if (sessionState.isActiveState || !updateResumeState) {
            Message oldPingMessage = pingMessage;
            channel.sendMessage(buildPingString()).queue(createdMessage -> pingMessage = createdMessage);
            Message oldMainMessage = mainMessage;
            channel.sendMessage(buildEmbed(currentTime)).queue(createdMessage -> {
                mainMessage = createdMessage;
                updateMessageEmojis();
            });
            if (settings.hasSetting(BooleanSetting.DELETE)) {
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
        if (settings.hasSetting(BooleanSetting.PINGS)) {
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
        if (!sessionState.isActiveState) {
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
            if (sessionState.isActiveState) {
                throw new BadStateException("Uh oh, someone forgot to set the timer, bad Tatsuya");
            }
            throw new BadUserInputException("Session is currently suspended");
        }
        return minutesToTimeString(minutesBetweenTwoTimes(currentTime, nextPing)) + " until " + sessionState.stateGeneral;
    }

    public void resetTime(Instant currentTime) {
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is currently suspended");
        }

        nextPing = currentTime.plus(settings.getStateDuration(sessionState), ChronoUnit.MINUTES);
        if (mainMessage != null) {
            mainMessage.editMessage(buildEmbed(currentTime)).queue();
        }
    }

    public void addTime(int minutes, Instant currentTime) {
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is currently suspended");
        }
        if (minutes <= 0) {
            throw new BadUserInputException("Please enter a number of minutes greater than 0");
        }
        if (minutes >= PomodoroSettings.maxDuration) {
            throw new BadUserInputException("Please enter a number of minutes less than than " + PomodoroSettings.maxDuration);
        }
        nextPing = nextPing.plus(minutes, ChronoUnit.MINUTES);
        update(currentTime, false);
    }

    public void removeTime(int minutes, Instant currentTime) {
        if (!sessionState.isActiveState) {
            throw new BadUserInputException("Session is currently suspended");
        }
        if (minutes <= 0) {
            throw new BadUserInputException("Please enter a number of minutes greater than 0");
        }
        if (minutes >= PomodoroSettings.maxDuration) {
            throw new BadUserInputException("Please enter a number of minutes less than than " + PomodoroSettings.maxDuration);
        }
        int timeDiff = minutesBetweenTwoTimes(currentTime, nextPing);
        if (timeDiff < minutes + 1) {
            throw new BadUserInputException("There's only " + minutesToTimeString(timeDiff) + " left! Can't lower it by " + minutes + " minutes!");
        }
        nextPing = nextPing.minus(minutes, ChronoUnit.MINUTES);
        update(currentTime, false);
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
        boolean isActiveState;

        SessionState(String stateTitle, String stateGeneral, boolean isActiveState, Color defaultColour, String defaultThumbnail) {
            this.stateTitle = stateTitle;
            this.stateGeneral = stateGeneral;
            this.defaultColour = defaultColour;
            this.defaultThumbnail = defaultThumbnail;
            this.isActiveState = isActiveState;
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
