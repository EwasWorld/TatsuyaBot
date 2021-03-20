import CoreBox.PomodoroSession;
import CoreBox.PomodoroSettings;
import ExceptionsBox.BadUserInputException;
import javassist.NotFoundException;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("ConstantConditions") // If a null pointer exception is unexpectedly thrown, the test should fail
public class PomodoroUnitTests {
    private final String memberName = "MemberName";
    private Member mockMember;
    private MessageChannel mockChannel;
    private MessageAction mockMessageAction;
    /**
     * List collects all mock messages created on {@link #mockChannel}.sendMessage()
     * Items are appended to the end, later messages are newer
     */
    private List<Message> mockMessages;
    private Instant start;
    /*
     * TODO Implement Tests:
     * New & start
     *   - Check built embed is correct
     *     - Check stats and time until next state update as intended
     *   - Check emojis are cleared and re-added correctly
     *   - Check start causes the message to be updated
     * Join & Leave (check displayed correctly, check ping works correctly)
     *   - With/without message/pings
     *   - Pings and message
     *   - Multiple users
     * Edit
     *   - Currently on a long break - edit to not have a long break?
     *   - Currently on a PAUSED long break - edit to not have a long break?
     *   - Edit timeout time in suspended state to lower than elapsed time?
     * Time & Settings commands
     * Pause & Resume
     *   - Ensure it resumes to the correct state
     *   - Check stats and work session counts
     *   - Double pause
     *   - Double resume
     * Skip
     *   - Ensure the next state is correct
     *   - Skip when suspended
     * Changing timings:
     *   - Reset, Bump, BigBump, Lower, BigLower
     *   - Check bounds
     * Stop
     *   - From all states
     * Ban & Unban
     *   - Try to join with/without message/pings
     * Misc
     *   - Check timeout works
     *   - Check update works?
     */

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BeforeEach
    public void setup() {
        start = Instant.now();
        mockMessages = new ArrayList<>();

        mockMember = mock(Member.class);
        when(mockMember.getEffectiveName()).thenReturn(memberName);

        mockMessageAction = mock(MessageAction.class);
        AuditableRestAction mockAuditableRestAction = mock(AuditableRestAction.class);
        mockChannel = mock(MessageChannel.class);
        when(mockChannel.sendMessage(any(MessageEmbed.class))).thenReturn(mockMessageAction);
        when(mockChannel.sendMessage(anyString())).thenReturn(mockMessageAction);
        doAnswer(ans -> {
            Message mockMessage = mock(Message.class);
            mockMessages.add(mockMessage);
            when(mockMessage.clearReactions()).thenReturn(mockAuditableRestAction);
            when(mockMessage.addReaction(anyString())).thenReturn(mockAuditableRestAction);
            when(mockMessage.delete()).thenReturn(mockAuditableRestAction);
            Consumer<Message> callback = (Consumer<Message>) ans.getArguments()[0];
            callback.accept(mockMessage);
            return null;
        }).when(mockMessageAction).queue(any(Consumer.class));
        doNothing().when(mockAuditableRestAction).queue();
    }

    /**
     * Quick fire through some valid argument strings to make sure they don't throw an exception
     * Ensure that the session can start
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "", // Empty string
            "10", // Work only
            "10 10", // Work/Break only
            "10 10 10 10", // Work/Break/Long break
            "10 10 pings:on auto:off", // Work/Break bool settings
            "pings:on auto:off", // Bool settings only
            "auto:off pings:on", // Bool settings different order
            "pInGs:on AuTo:off DELETE:on", // Crazy case bool setting
    })
    public void newSanityCheck(String args) {
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, args, start);
        session.userStartSession(start.plusSeconds(60));
    }

    /**
     * Quick fire through some invalid argument strings to make sure they do throw an exception
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "10 10 10", // Work/Break/Long break (invalid)
            "10 pings:on 10 auto:on", // Out of order settings
            "cheesepie:on", // Invalid bool option
            "pings:potato", // Invalid bool value (on/off)
            // Out of bounds numbers
            "-10 10",
            "10 -10",
            "10 10 -10 10",
            "10 10 10 -10",
            "10080 10 10 10", // 10080 minutes in a week
            "10 10 10 10080",
            // String number mixes
            "ch1ck3nWings",
            "w0bble",
            "c4ctus:on",
            "t3l3vision:on",
    })
    public void newInvalidSanityCheck(String args) {
        Assertions.assertThrows(BadUserInputException.class, () ->
                new PomodoroSession(mockMember, mockChannel, args, start)
        );
    }

    /**
     * Ensure an empty string sets the correct defaults
     * Ensure that the session can start
     */
    @Test
    public void newEmpty() {
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "", start);
        Assertions.assertEquals(25, session.getSettings().getStateDuration(PomodoroSession.SessionState.WORK));
        Assertions.assertEquals(10, session.getSettings().getStateDuration(PomodoroSession.SessionState.BREAK));
        Assertions.assertNull(session.getSettings().getStateDuration(PomodoroSession.SessionState.LONG_BREAK));
        Assertions.assertNull(session.getSettings().getWorkSessionsBeforeLongBreak());
        Set<PomodoroSession.BooleanSetting> settings = new HashSet<>() {
            {
                add(PomodoroSession.BooleanSetting.PINGS);
                add(PomodoroSession.BooleanSetting.AUTO);
                add(PomodoroSession.BooleanSetting.DELETE);
            }
        };
        for (PomodoroSession.BooleanSetting setting : PomodoroSession.BooleanSetting.values()) {
            Assertions.assertEquals(settings.contains(setting), session.getSettings().getBooleanSetting(setting));
        }
        session.userStartSession(start.plusSeconds(60));
    }

    /**
     * Ensure that numbers are interpreted in the right order
     * Ensure that the session can start
     */
    @Test
    public void newFullNumbers() {
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "10 11 12 13", start);
        Assertions.assertEquals(10, session.getSettings().getStateDuration(PomodoroSession.SessionState.WORK));
        Assertions.assertEquals(11, session.getSettings().getStateDuration(PomodoroSession.SessionState.BREAK));
        Assertions.assertEquals(12, session.getSettings().getStateDuration(PomodoroSession.SessionState.LONG_BREAK));
        Assertions.assertEquals(13, session.getSettings().getWorkSessionsBeforeLongBreak());
        session.userStartSession(start.plusSeconds(60));
    }

    /**
     * Ensure that bool settings are set correctly
     * Ensure that the session can start
     */
    @Test
    public void newFullBoolSettings() {
        PomodoroSession.BooleanSetting[] settings = PomodoroSession.BooleanSetting.values();
        StringBuilder argString = new StringBuilder();
        for (int i = 0; i < settings.length; i++) {
            argString.append(i != 0 ? " " : "");
            argString.append(settings[i].toString());
            argString.append(PomodoroSettings.boolSettingDeliminator);
            argString.append(i % 2 == 0 ? PomodoroSettings.boolSettingOn : PomodoroSettings.boolSettingOff);
        }
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, argString.toString(), start);
        for (int i = 0; i < settings.length; i++) {
            Assertions.assertEquals(i % 2 == 0, session.getSettings().getBooleanSetting(settings[i]));
        }
        session.userStartSession(start.plusSeconds(60));
    }

    /**
     * Generally ensure that the embeds are generated correctly for every state
     */
    @Test
    public void generalCheckEmbeds() throws Exception {
        Instant currentTime = start;
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "25 10 30 2 images:on", start);
        session.userStartSession(currentTime); // Start work
        currentTime = currentTime.plusSeconds(60 * 25);
        session.update(currentTime, true); // Start break
        session.update(currentTime, true); // Start work
        currentTime = currentTime.plusSeconds(60 * 25);
        session.update(currentTime, true); // Start long break
        session.userPauseSession(currentTime);
        session.userStopSession(currentTime);

        ArgumentCaptor<MessageEmbed> argumentCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        // Return is handled by the mock, this just captures arguments
        //noinspection ResultOfMethodCallIgnored
        verify(mockChannel, atLeastOnce()).sendMessage(argumentCaptor.capture());
        List<MessageEmbed> embeds = argumentCaptor.getAllValues();

        MessageEmbed notStartedEmbed = embeds.get(0);
        List<MessageEmbed.Field> notStartedFields = notStartedEmbed.getFields();
        Assertions.assertTrue(notStartedEmbed.getTitle().contains("NOT STARTED"));
        Assertions.assertEquals(PomodoroSession.SessionState.NOT_STARTED.getDefaultColour(), notStartedEmbed.getColor());
        Assertions.assertEquals("Nothing submitted", EmbedFields.WORKING_ON.find(notStartedFields).getValue());
        Assertions.assertEquals("Timer not started", notStartedEmbed.getDescription());

        String notStartedSettingsMessage = EmbedFields.SESSION_SETTINGS.find(notStartedFields).getValue();
        String[] newSettingsMessageSplit = notStartedSettingsMessage.split("\n");
        Assertions.assertEquals("Work: 25 mins, Break: 10 mins", newSettingsMessageSplit[0]);
        Assertions.assertEquals("Work sessions before long break: 2, Long break: 30 mins", newSettingsMessageSplit[1]);
        Assertions.assertEquals("Session created by: " + memberName, newSettingsMessageSplit[2]);

        String[] notStartedStatsMessage = EmbedFields.COMPLETED_STATS.find(notStartedFields).getValue().split("\n");
        Assertions.assertEquals("Started: --:--", notStartedStatsMessage[0]);
        Assertions.assertEquals("Completed work sessions: 0", notStartedStatsMessage[1]);
        Assertions.assertEquals("Total study time: 0 mins", notStartedStatsMessage[2]);

        int notStartedBlankFields = EmbedFields.countBlank(notStartedFields);
        Assertions.assertEquals(1, notStartedBlankFields);
        String notStartedParticipantsMessage = EmbedFields.PARTICIPANTS.find(notStartedFields).getValue();
        String notStartedWorkingMessage = EmbedFields.WORKING_ON.find(notStartedFields).getValue();

        /*
         * Work
         */
        MessageEmbed workEmbed = embeds.get(1);
        List<MessageEmbed.Field> workFields = workEmbed.getFields();
        Assertions.assertTrue(workEmbed.getTitle().contains("WORKING"));
        Assertions.assertEquals(PomodoroSession.SessionState.WORK.getDefaultColour(), workEmbed.getColor());
        Assertions.assertEquals(PomodoroSession.SessionState.WORK.getDefaultImage(), workEmbed.getImage().getUrl());
        Assertions.assertEquals("25 mins until break\n" +
                "1 work sessions until long break (not including this one)", workEmbed.getDescription());

        String[] workStatsMessage = EmbedFields.COMPLETED_STATS.find(workFields).getValue().split("\n");
        Assertions.assertEquals("Started: " + ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).format(session.getSettings().getDateTimeFormatter()), workStatsMessage[0]);
        Assertions.assertEquals("Completed work sessions: 0", workStatsMessage[1]);
        Assertions.assertEquals("Total study time: 0 mins", workStatsMessage[2]);

        /*
         * Break
         */
        MessageEmbed breakEmbed = embeds.get(2);
        List<MessageEmbed.Field> breakFields = breakEmbed.getFields();
        Assertions.assertTrue(breakEmbed.getTitle().contains("BREAK"));
        Assertions.assertEquals(PomodoroSession.SessionState.BREAK.getDefaultColour(), breakEmbed.getColor());
        Assertions.assertEquals(PomodoroSession.SessionState.BREAK.getDefaultImage(), breakEmbed.getImage().getUrl());
        Assertions.assertEquals("10 mins until work\n" +
                "1 work sessions until long break", breakEmbed.getDescription());

        String[] breakStatsMessage = EmbedFields.COMPLETED_STATS.find(breakFields).getValue().split("\n");
        Assertions.assertEquals(workStatsMessage[0], breakStatsMessage[0]);
        Assertions.assertEquals("Completed work sessions: 1", breakStatsMessage[1]);
        Assertions.assertEquals("Total study time: 25 mins", breakStatsMessage[2]);

        /*
         * Work
         */
        // Skip checking second work session
        MessageEmbed workEmbed2 = embeds.get(3);
        Assertions.assertEquals("25 mins until long break", workEmbed2.getDescription());

        /*
         * Long break
         */
        MessageEmbed longBreakEmbed = embeds.get(4);
        List<MessageEmbed.Field> longBreakFields = longBreakEmbed.getFields();
        Assertions.assertTrue(longBreakEmbed.getTitle().contains("LONG BREAK"));
        Assertions.assertEquals(PomodoroSession.SessionState.LONG_BREAK.getDefaultColour(), longBreakEmbed.getColor());
        Assertions.assertEquals(PomodoroSession.SessionState.LONG_BREAK.getDefaultImage(), longBreakEmbed.getImage().getUrl());
        Assertions.assertEquals("30 mins until work", longBreakEmbed.getDescription());

        String longBreakStatsMessage = EmbedFields.COMPLETED_STATS.find(longBreakFields).getValue();
        String[] longBreakStatsMessageSplit = longBreakStatsMessage.split("\n");
        Assertions.assertEquals(workStatsMessage[0], longBreakStatsMessageSplit[0]);
        Assertions.assertEquals("Completed work sessions: 2", longBreakStatsMessageSplit[1]);
        Assertions.assertEquals("Total study time: 50 mins", longBreakStatsMessageSplit[2]);

        /*
         * Paused
         */
        MessageEmbed pausedEmbed = embeds.get(5);
        List<MessageEmbed.Field> pausedFields = pausedEmbed.getFields();
        Assertions.assertTrue(pausedEmbed.getTitle().contains("PAUSED"));
        Assertions.assertEquals(PomodoroSession.SessionState.PAUSED.getDefaultColour(), pausedEmbed.getColor());
        Assertions.assertEquals(PomodoroSession.SessionState.PAUSED.getDefaultImage(), pausedEmbed.getImage().getUrl());
        Assertions.assertEquals("Session is paused. Resume to continue long break", pausedEmbed.getDescription());
        Assertions.assertEquals(longBreakStatsMessage, EmbedFields.COMPLETED_STATS.find(pausedFields).getValue());

        /*
         * Finished
         */
        MessageEmbed finishedEmbed = embeds.get(6);
        List<MessageEmbed.Field> finishedFields = finishedEmbed.getFields();
        Assertions.assertTrue(finishedEmbed.getTitle().contains("FINISHED"));
        Assertions.assertEquals(PomodoroSession.SessionState.FINISHED.getDefaultColour(), finishedEmbed.getColor());
        Assertions.assertEquals(PomodoroSession.SessionState.FINISHED.getDefaultImage(), finishedEmbed.getImage().getUrl());
        Assertions.assertEquals("Session completed", finishedEmbed.getDescription());
        Assertions.assertEquals(longBreakStatsMessage, EmbedFields.COMPLETED_STATS.find(finishedFields).getValue());

        /*
         * Unchanging items (check all others against first)
         */
        for (int i = 1; i < embeds.size(); i++) {
            List<MessageEmbed.Field> fields = embeds.get(i).getFields();
            Assertions.assertEquals(notStartedSettingsMessage, EmbedFields.SESSION_SETTINGS.find(fields).getValue());
            Assertions.assertEquals(notStartedParticipantsMessage, EmbedFields.PARTICIPANTS.find(fields).getValue());
            Assertions.assertEquals(notStartedWorkingMessage, EmbedFields.WORKING_ON.find(fields).getValue());
            Assertions.assertEquals(notStartedBlankFields, EmbedFields.countBlank(fields));
        }
    }


    @Test
    public void newCheckEmojis() {
        // TODO
    }

    private enum EmbedFields {
        PARTICIPANTS("Ping"), WORKING_ON("working"), COMPLETED_STATS("Completed"), SESSION_SETTINGS("Settings");

        String titleStringContains;

        EmbedFields(String titleStringContains) {
            this.titleStringContains = titleStringContains;
        }

        public MessageEmbed.Field find(List<MessageEmbed.Field> fields) throws NotFoundException {
            for (MessageEmbed.Field field : fields) {
                if (field.getName().contains(this.titleStringContains)) {
                    return field;
                }
            }
            throw new NotFoundException(this.toString() + " was not found");
        }

        public static int countBlank(List<MessageEmbed.Field> fields) {
            int count = 0;
            for (MessageEmbed.Field field : fields) {
                if (field.getName().equals("\u200E") && field.getValue().equals("\u200E")) {
                    count++;
                }
            }
            return count;
        }
    }
}
