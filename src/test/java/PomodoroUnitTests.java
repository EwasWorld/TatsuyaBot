import CoreBox.PomodoroSession;
import CoreBox.PomodoroSettings;
import ExceptionsBox.BadUserInputException;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PomodoroUnitTests {
    Member mockedMember;
    MessageChannel mockedChannel;
    Instant start;
    /*
     * Test plan:
     *
     * New & start
     *   - Check built embed is correct
     *   - Check emojis are cleared and re-added correctly
     *   - Check start causes the message to be updated
     *   - Args
     *     - Empty string
     *     - Work/Break only
     *     - Work/Break/Long break
     *     - Work/Break bool settings
     *     - Bool settings only
     *     - Out of bounds numbers
     *     - Out of order settings
     *     - Invalid bool option
     *     - Invalid bool value (on/off)
     * Join & Leave (check displayed correctly, check ping works correctly)
     *   - With/without message/pings
     *   - Pings and message
     *   - Multiple users
     * Edit
     *   - Currently on a long break - edit to not have a long break?
     *   - Currently on a PAUSED long break - edit to not have a long break?
     *   - Edit timeout time in suspended state to lower than elapsed time?
     * Time & Settings
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
     */

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BeforeEach
    public void setup() {
        start = Instant.now();

        MessageAction mockAction = mock(MessageAction.class);
        AuditableRestAction mockAuditableRestAction = mock(AuditableRestAction.class);
        mockedMember = mock(Member.class);
        mockedChannel = mock(MessageChannel.class);
        when(mockedChannel.sendMessage(any(MessageEmbed.class))).thenReturn(mockAction);
        when(mockedChannel.sendMessage(anyString())).thenReturn(mockAction);
        doAnswer(ans -> {
            Message mockMessage = mock(Message.class);
            when(mockMessage.clearReactions()).thenReturn(mockAuditableRestAction);
            when(mockMessage.addReaction(anyString())).thenReturn(mockAuditableRestAction);
            when(mockMessage.delete()).thenReturn(mockAuditableRestAction);
            Consumer<Message> callback = (Consumer<Message>) ans.getArguments()[0];
            callback.accept(mockMessage);
            return null;
        }).when(mockAction).queue(any(Consumer.class));
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
        PomodoroSession session = new PomodoroSession(mockedMember, mockedChannel, args, start);
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
                new PomodoroSession(mockedMember, mockedChannel, args, start)
        );
    }

    /**
     * Ensure an empty string sets the correct defaults
     * Ensure that the session can start
     */
    @Test
    public void newEmpty() {
        PomodoroSession session = new PomodoroSession(mockedMember, mockedChannel, "", start);
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
        PomodoroSession session = new PomodoroSession(mockedMember, mockedChannel, "10 11 12 13", start);
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
        PomodoroSession session = new PomodoroSession(mockedMember, mockedChannel, argString.toString(), start);
        for (int i = 0; i < settings.length; i++) {
            Assertions.assertEquals(i % 2 == 0, session.getSettings().getBooleanSetting(settings[i]));
        }
        session.userStartSession(start.plusSeconds(60));
    }
}
