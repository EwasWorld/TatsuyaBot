import BotFrameworkBox.Emoji;
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
import java.util.*;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("ConstantConditions") // If a null pointer exception is unexpectedly thrown, the test should fail
public class PomodoroUnitTests {
    private final String memberName = "MemberName";
    private Member mockMember;
    private MessageChannel mockChannel;
    private static MessageAction mockMessageAction;
    /**
     * List collects all MockMessages created
     * Items are appended to the end, later messages are newer
     */
    private static List<MockMessage> mockMessages;
    private Instant start;

    /*
     * TODO Implement Tests:
     * Join & Leave (check displayed correctly, check ping works correctly)
     *   - With/without message/pings
     *   - Pings and message
     *   - Multiple users
     * Edit
     *   - Currently on a long break - edit to not have a long break?
     *   - Currently on a PAUSED long break - edit to not have a long break?
     *   - Edit timeout time in suspended state to lower than elapsed time?
     * Time & Settings commands
     * Changing timings:
     *   - Reset, Bump, BigBump, Lower, BigLower
     *   - Check bounds
     * Ban & Unban
     *   - Try to join with/without message/pings
     * Misc
     *   - Check timeout works
     *   - Check update works?
     *   - Boolean settings work as intended
     */

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        start = Instant.now();
        mockMessages = new ArrayList<>();

        mockMember = mock(Member.class);
        when(mockMember.getEffectiveName()).thenReturn(memberName);

        mockMessageAction = mock(MessageAction.class);
        mockChannel = mock(MessageChannel.class);

        /*
         * Capture message arguments and save generated Message mock
         */
        when(mockChannel.sendMessage(any(MessageEmbed.class))).thenAnswer(ans -> {
            new MockMessage((MessageEmbed) ans.getArguments()[0], true);
            return mockMessageAction;
        });
        when(mockChannel.sendMessage(anyString())).thenAnswer(ans -> {
            new MockMessage((String) ans.getArguments()[0], true);
            return mockMessageAction;
        });
        doAnswer(ans -> {
            Consumer<Message> callback = (Consumer<Message>) ans.getArguments()[0];
            callback.accept(mockMessages.get(mockMessages.size() - 1).mock);
            return null;
        }).when(mockMessageAction).queue(any(Consumer.class));
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
        final String statsStarted = "Started: %s";
        final String statsWorkSessions = "Completed work sessions: %d";
        final String statsStudyTime = "Total study time: %d mins";

        /*
         * Run through all states
         */
        Instant currentTime = start;
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "25 10 30 2 images:on", currentTime);
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

        /*
         * Not Started
         */
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
        Assertions.assertEquals(String.format(statsStarted, "--:--"), notStartedStatsMessage[0]);
        Assertions.assertEquals(String.format(statsWorkSessions, 0), notStartedStatsMessage[1]);
        Assertions.assertEquals(String.format(statsStudyTime, 0), notStartedStatsMessage[2]);

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
        Assertions.assertEquals("25 mins until break\n1 work session until long break (not including this one)", workEmbed.getDescription());

        String[] workStatsMessage = EmbedFields.COMPLETED_STATS.find(workFields).getValue().split("\n");
        Assertions.assertEquals(String.format(statsStarted, ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).format(session.getSettings().getDateTimeFormatter())), workStatsMessage[0]);
        Assertions.assertEquals(String.format(statsWorkSessions, 0), workStatsMessage[1]);
        Assertions.assertEquals(String.format(statsStudyTime, 0), workStatsMessage[2]);

        /*
         * Break
         */
        MessageEmbed breakEmbed = embeds.get(2);
        List<MessageEmbed.Field> breakFields = breakEmbed.getFields();
        Assertions.assertTrue(breakEmbed.getTitle().contains("BREAK"));
        Assertions.assertEquals(PomodoroSession.SessionState.BREAK.getDefaultColour(), breakEmbed.getColor());
        Assertions.assertEquals(PomodoroSession.SessionState.BREAK.getDefaultImage(), breakEmbed.getImage().getUrl());
        Assertions.assertEquals("10 mins until work\n1 work session until long break", breakEmbed.getDescription());

        String breakStatsMessage = EmbedFields.COMPLETED_STATS.find(breakFields).getValue();
        String[] breakStatsMessageSplit = breakStatsMessage.split("\n");
        Assertions.assertEquals(workStatsMessage[0], breakStatsMessageSplit[0]);
        Assertions.assertEquals(String.format(statsWorkSessions, 1), breakStatsMessageSplit[1]);
        Assertions.assertEquals(String.format(statsStudyTime, 25), breakStatsMessageSplit[2]);

        /*
         * Work
         */
        // Skip checking second work session
        MessageEmbed workEmbed2 = embeds.get(3);
        Assertions.assertEquals("25 mins until long break", workEmbed2.getDescription());
        Assertions.assertEquals(breakStatsMessage, EmbedFields.COMPLETED_STATS.find(workEmbed2.getFields()).getValue());

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
        Assertions.assertEquals(String.format(statsWorkSessions, 2), longBreakStatsMessageSplit[1]);
        Assertions.assertEquals(String.format(statsStudyTime, 50), longBreakStatsMessageSplit[2]);

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


    /**
     * Check that the emojis added to each state are correct and that no emojis are added to other messages
     */
    @Test
    public void generalCheckEmojis() {
        Instant currentTime = start;
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "25 10 30 2", currentTime);
        session.userStartSession(currentTime); // Start work
        currentTime = currentTime.plusSeconds(60 * 25);
        session.update(currentTime, true); // Start break
        session.update(currentTime, true); // Start work
        currentTime = currentTime.plusSeconds(60 * 25);
        session.update(currentTime, true); // Start long break
        session.userPauseSession(currentTime);
        session.userStopSession(currentTime);

        final Emoji[] suspendedEmojis = new Emoji[]{Emoji.PLAY, Emoji.PERSON_HAND_RAISED, Emoji.PERSON_NO_HANDS, Emoji.STOP};
        final Emoji[] activeEmojis = new Emoji[]{Emoji.SKIP, Emoji.PAUSE, Emoji.PERSON_HAND_RAISED, Emoji.PERSON_NO_HANDS, Emoji.UP_ARROW, Emoji.DOUBLE_UP_ARROW, Emoji.DOWN_ARROW, Emoji.DOUBLE_DOWN_ARROW, Emoji.STOP};
        final Emoji[][] messagesExpectedEmojis = new Emoji[][]{
                suspendedEmojis, // Not Started
                activeEmojis, // Work
                activeEmojis, // Break
                activeEmojis, // Work 2
                activeEmojis, // Long Break
                suspendedEmojis, // Paused
                new Emoji[]{} // Finished
        };

        int messagesProcessed = 0;
        for (MockMessage mockMessage : mockMessages) {
            if (mockMessage.contents.contains("Bangs Pots")) {
                // Return is handled by the mock, this just captures arguments
                //noinspection ResultOfMethodCallIgnored
                verify(mockMessage.mock, times(0)).clearReactions();
                //noinspection ResultOfMethodCallIgnored
                verify(mockMessage.mock, times(0)).addReaction(anyString());
                continue;
            }
            System.out.println("Processing message: " + messagesProcessed);

            ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
            // Return is handled by the mock, this just captures arguments
            //noinspection ResultOfMethodCallIgnored
            verify(mockMessage.mock, times(1)).clearReactions();
            //noinspection ResultOfMethodCallIgnored
            verify(mockMessage.mock, times(messagesExpectedEmojis[messagesProcessed].length)).addReaction(argumentCaptor.capture());

            List<String> expectedEmoji = new ArrayList<>();
            for (Emoji emoji : messagesExpectedEmojis[messagesProcessed]) {
                expectedEmoji.add(emoji.getUnicode());
            }
            Assertions.assertEquals(expectedEmoji, argumentCaptor.getAllValues());
            messagesProcessed++;
        }
        Assertions.assertEquals(messagesExpectedEmojis.length, messagesProcessed);
    }

    /**
     * Check the timings displayed on the embed are accurate
     */
    @Test
    public void checkEmbedTimings() throws Exception {
        final String statsWorkSessions = "Completed work sessions: %d";
        final String statsStudyTime = "Total study time: %d mins";

        Instant currentTime = start;
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "25 20 30 2", currentTime);
        session.userStartSession(currentTime); // Start work
        currentTime = currentTime.plusSeconds(60 * 2);
        session.update(currentTime, false); // 2 mins elapsed
        currentTime = currentTime.plusSeconds(60 * 8);
        session.update(currentTime, false); // 10 mins elapsed
        session.update(currentTime, true); // Start break
        currentTime = currentTime.plusSeconds(60 * 2);
        session.update(currentTime, false); // 2 mins elapsed
        currentTime = currentTime.plusSeconds(60 * 8);
        session.update(currentTime, false); // 10 mins elapsed

        int[] expectedStudyTime = new int[]{
                0, // Not started
                0, // Work
                2, // Work 2 mins elapsed
                10, // Work 10 mins elapsed
                10, // Break
                10, // Break 2 mins elapsed
                10, // Break 10 mins elapsed
        };
        String[] expectedDescriptions = new String[]{
                "Timer not started", // Not started
                "25 mins until break\n1 work session until long break (not including this one)", // Work
                "23 mins until break\n1 work session until long break (not including this one)", // Work 2 mins elapsed
                "15 mins until break\n1 work session until long break (not including this one)", // Work 10 mins elapsed
                "20 mins until work\n1 work session until long break", // Break
                "18 mins until work\n1 work session until long break", // Break 2 mins elapsed
                "10 mins until work\n1 work session until long break" // Break 10 mins elapsed
        };
        // Sanity check
        Assertions.assertEquals(expectedStudyTime.length, expectedDescriptions.length);

        int messagesProcessed = 0;
        for (MockMessage mockMessage : mockMessages) {
            MessageEmbed embed = mockMessage.embed;
            if (embed == null) {
                continue;
            }
            System.out.println("Processing message: " + messagesProcessed);
            Assertions.assertEquals(expectedDescriptions[messagesProcessed], embed.getDescription());
            String[] stats = EmbedFields.COMPLETED_STATS.find(embed.getFields()).getValue().split("\n");
            Assertions.assertEquals(String.format(statsWorkSessions, messagesProcessed < 4 ? 0 : 1), stats[1]);
            Assertions.assertEquals(String.format(statsStudyTime, expectedStudyTime[messagesProcessed]), stats[2]);

            messagesProcessed++;
        }
        Assertions.assertEquals(expectedStudyTime.length, messagesProcessed);
    }

    /**
     * Ensure that
     * - skipping cycles through the correct states
     * - pausing and resuming works correctly and doesn't interfere with the skipping cycling
     * - double pausing/Resuming is rejected
     */
    @Test
    public void generalStateTransitions() {
        int workBeforeLongBreak = 3;
        PomodoroSession session = new PomodoroSession(mockMember, mockChannel, "25 20 30 " + workBeforeLongBreak, start);

        Assertions.assertEquals(PomodoroSession.SessionState.NOT_STARTED, session.getSessionState());
        Assertions.assertThrows(BadUserInputException.class, () -> session.update(start, true));
        Assertions.assertThrows(BadUserInputException.class, () -> session.userPauseSession(start));
        session.userStartSession(start);

        /*
         * Skipping
         */
        for (int j = 0; j < 3; j++) {
            for (int i = 0; ; i++) {
                System.out.println("No pauses round: " + j + " " + i);
                Assertions.assertEquals(PomodoroSession.SessionState.WORK, session.getSessionState());
                session.update(start, true);
                if (i == workBeforeLongBreak - 1) {
                    break;
                }
                Assertions.assertEquals(PomodoroSession.SessionState.BREAK, session.getSessionState());
                session.update(start, true);
            }
            Assertions.assertEquals(PomodoroSession.SessionState.LONG_BREAK, session.getSessionState());
            session.update(start, true);
        }

        /*
         * With pauses
         */
        for (int i = 0; ; i++) {
            System.out.println("Pauses round: " + i);

            Assertions.assertEquals(PomodoroSession.SessionState.WORK, session.getSessionState());
            Assertions.assertThrows(BadUserInputException.class, () -> session.userResumeSession(start));
            Assertions.assertEquals(PomodoroSession.SessionState.WORK, session.getSessionState());
            session.userPauseSession(start);
            Assertions.assertEquals(PomodoroSession.SessionState.PAUSED, session.getSessionState());
            Assertions.assertThrows(BadUserInputException.class, () -> session.update(start, true));
            session.userResumeSession(start);
            Assertions.assertEquals(PomodoroSession.SessionState.WORK, session.getSessionState());
            session.update(start, true);

            if (i == workBeforeLongBreak - 1) {
                break;
            }
            Assertions.assertEquals(PomodoroSession.SessionState.BREAK, session.getSessionState());
            Assertions.assertThrows(BadUserInputException.class, () -> session.userResumeSession(start));
            Assertions.assertEquals(PomodoroSession.SessionState.BREAK, session.getSessionState());
            session.userPauseSession(start);
            Assertions.assertEquals(PomodoroSession.SessionState.PAUSED, session.getSessionState());
            Assertions.assertThrows(BadUserInputException.class, () -> session.update(start, true));
            session.userResumeSession(start);
            Assertions.assertEquals(PomodoroSession.SessionState.BREAK, session.getSessionState());
            session.update(start, true);
        }
        Assertions.assertEquals(PomodoroSession.SessionState.LONG_BREAK, session.getSessionState());
        Assertions.assertThrows(BadUserInputException.class, () -> session.userResumeSession(start));
        Assertions.assertEquals(PomodoroSession.SessionState.LONG_BREAK, session.getSessionState());
        session.userPauseSession(start);
        Assertions.assertEquals(PomodoroSession.SessionState.PAUSED, session.getSessionState());
        Assertions.assertThrows(BadUserInputException.class, () -> session.update(start, true));
        Assertions.assertThrows(BadUserInputException.class, () -> session.userPauseSession(start));
        session.userResumeSession(start);
        Assertions.assertEquals(PomodoroSession.SessionState.LONG_BREAK, session.getSessionState());
        session.update(start, true);

        /*
         * Finish
         */
        session.userStopSession(start);
        Assertions.assertEquals(PomodoroSession.SessionState.FINISHED, session.getSessionState());
        Assertions.assertThrows(BadUserInputException.class, () -> session.update(start, true));
        Assertions.assertThrows(BadUserInputException.class, () -> session.userPauseSession(start));
    }

    /**
     * Test stop from all states
     */
    @Test
    public void generalStop() {
        final PomodoroSession[] session = {null};
        ExecutableItem[] actions = {
                () -> { }, // Not started
                () -> session[0].userStartSession(start), // Start work
                () -> session[0].update(start, true), // Start break
                () -> session[0].update(start, true), // Start work
                () -> session[0].update(start, true), // Start long break
                () -> session[0].userPauseSession(start),
        };

        for (int j = 0; j < actions.length; j++) {
            session[0] = new PomodoroSession(mockMember, mockChannel, "25 10 30 2", start);
            for (int i = 0; i < j; i++) {
                actions[i].execute();
            }
            session[0].userStopSession(start);
            Assertions.assertEquals(PomodoroSession.SessionState.FINISHED, session[0].getSessionState());
        }
        Assertions.assertThrows(BadUserInputException.class, () -> session[0].userStopSession(start));
    }

    /**
     * Helper function for calling methods in a loop
     */
    private interface ExecutableItem {
        void execute();
    }

    /**
     * Information about a message that was sent to a channel
     */
    private static class MockMessage {
        /**
         * The mock that was generated to represent this message in Discord
         */
        Message mock;
        String contents;
        /**
         * new or edit
         */
        boolean isNew;
        /**
         * Null if the message was generated via a string
         */
        MessageEmbed embed = null;

        public MockMessage(String contents, boolean isNew) {
            mock = generate();
            this.contents = contents;
            this.isNew = isNew;
            mockMessages.add(this);
        }

        public MockMessage(MessageEmbed contents, boolean isNew) {
            this(embedToString(contents), isNew);
            embed = contents;
        }

        @SuppressWarnings("unchecked")
        private static Message generate() {
            AuditableRestAction mockAuditableRestAction = mock(AuditableRestAction.class);

            Message mockMessage = mock(Message.class);
            when(mockMessage.editMessage(any(MessageEmbed.class))).thenAnswer(editAns -> {
                new MockMessage((MessageEmbed) editAns.getArguments()[0], false);
                return mockMessageAction;
            });
            when(mockMessage.editMessage(anyString())).thenAnswer(editAns -> {
                new MockMessage((MessageEmbed) editAns.getArguments()[0], false);
                return mockMessageAction;
            });
            when(mockMessage.clearReactions()).thenReturn(mockAuditableRestAction);
            when(mockMessage.addReaction(anyString())).thenReturn(mockAuditableRestAction);
            when(mockMessage.delete()).thenReturn(mockAuditableRestAction);

            doNothing().when(mockAuditableRestAction).queue();
            return mockMessage;
        }

        /**
         * @return stringified message embed. Does not necessarily exactly represent the embed's full contents
         */
        private static String embedToString(MessageEmbed embed) {
            String contentString = embed.getTitle();
            contentString += embed.getDescription();
            return contentString;
        }
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
