package CoreBox;

import java.awt.*;
import java.util.*;
import java.util.List;

import CoreBox.PomodoroSession.*;
import ExceptionsBox.BadStateException;
import ExceptionsBox.BadUserInputException;
import TatsuyaCommands.PomodoroCommand;
import net.dv8tion.jda.api.Permission;
import org.jetbrains.annotations.Nullable;

import static CoreBox.PomodoroSession.minutesToDisplayString;

/**
 * Settings such as work/study split for a particular pomodoro session
 */
public class PomodoroSettings {
    /**
     * Maximum allowed time input in minutes
     */
    public static final int maxDuration = 60 * 5;
    /**
     * Minimum allowed time input in minutes
     */
    public static final int minDuration = 5;
    public static final int minWorkSessionsBeforeLongBreak = 1;
    public static final int maxWorkSessionsBeforeLongBreak = 30;

    private final Map<SessionState, StateInfo> defaultStates = new HashMap<>() {
        {
            put(SessionState.WORK, new StateInfo(SessionState.WORK, 25, Color.BLUE, SessionState.WORK.defaultImage));
            put(SessionState.BREAK, new StateInfo(SessionState.BREAK, 10, Color.CYAN, SessionState.BREAK.defaultImage));
            put(SessionState.LONG_BREAK, new StateInfo(SessionState.LONG_BREAK, null, Color.CYAN, SessionState.LONG_BREAK.defaultImage));
            put(SessionState.NOT_STARTED, new StateInfo(SessionState.NOT_STARTED, null, Color.ORANGE, SessionState.NOT_STARTED.defaultImage));
            put(SessionState.PAUSED, new StateInfo(SessionState.PAUSED, null, Color.ORANGE, SessionState.PAUSED.defaultImage));
            put(SessionState.FINISHED, new StateInfo(SessionState.FINISHED, null, null, SessionState.FINISHED.defaultImage));
        }
    };
    private final Map<SessionState, StateInfo> states = new HashMap<>(defaultStates);

    /**
     * Presence in the map indicates the setting is on
     */
    private Set<BooleanSetting> booleanSettings = new HashSet<>() {
        {
            add(BooleanSetting.PINGS);
            add(BooleanSetting.AUTO);
            add(BooleanSetting.DELETE);
        }
    };
    /**
     * This setting also dictates whether the long break function will be used
     */
    private Integer workSessionsBeforeLongBreak = null;
    /**
     * For the non-active states, the pomodoro session will be cancelled after this amount of inactivity
     */
    private int timeoutDuration = 60;
    private Set<String> bannedMembers = new HashSet<>();
    // TODO member.hasPermission(Permission.MANAGE_SERVER, Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL);
    private Set<Permission> adminPermissions = new HashSet<>();

    public PomodoroSettings() {
    }

    /**
     * Store information which the clan can change about each state
     */
    private class StateInfo {
        private final SessionState state;
        private Integer duration;
        Color colour;
        String image;

        public StateInfo(SessionState state) {
            this.state = state;
            StateInfo defaultInfo = defaultStates.get(state);
            this.duration = defaultInfo.duration;
            this.colour = defaultInfo.colour;
            this.image = defaultInfo.image;
        }


        public StateInfo(SessionState state, Integer duration, Color colour, String image) {
            this.state = state;
            StateInfo defaultInfo = defaultStates != null ? defaultStates.get(state) : null;
            this.duration = duration != null ? duration : (defaultInfo != null ? defaultInfo.duration : null);
            this.colour = colour != null ? colour : (defaultInfo != null ? defaultInfo.colour : null);
            this.image = image != null ? image : (defaultInfo != null ? defaultInfo.image : null);
        }

        /**
         * @throws BadStateException if state type is not active or for null values being assigned to WORK or BREAK states
         */
        public void setDuration(Integer duration) {
            if (!state.isActiveState) {
                throw new BadStateException("Suspended states cannot have a duration");
            }
            if (duration == null && (state == SessionState.WORK || state == SessionState.BREAK)) {
                throw new BadStateException("Cannot have a null duration on work or break");
            }
            checkDuration(duration);
            this.duration = duration;
        }
    }

    public void setStateInfo(SessionState state, @Nullable Color color, @Nullable String thumbnail, @Nullable Integer duration) {
        final StateInfo info;
        if (states.containsKey(state)) {
            info = states.get(state);
        }
        else {
            info = new StateInfo(state);
        }

        if (color != null) {
            info.colour = color;
        }
        if (thumbnail != null) {
            info.image = thumbnail;
        }
        if (duration != null) {
            info.setDuration(duration);
        }
    }

    public void setBooleanSettings(Set<BooleanSetting> booleanSettings) {
        this.booleanSettings = booleanSettings;
    }

    public void setTimeoutDuration(int timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public void setBannedMembers(Set<String> bannedMembers) {
        this.bannedMembers = bannedMembers;
    }

    public void setAdminPermissions(Set<Permission> adminPermissions) {
        this.adminPermissions = adminPermissions;
    }

    /**
     * Check that non-null durations come between the {@link #maxDuration} and {@link #minDuration}
     *
     * @throws BadUserInputException if outside of bounds
     */
    public void checkDuration(Integer duration) {
        if (duration == null) {
            return;
        }
        if (duration > maxDuration) {
            throw new BadUserInputException("Maximum duration: " + minutesToDisplayString(maxDuration));
        }
        if (duration < minDuration) {
            throw new BadUserInputException("Minimum duration: " + minutesToDisplayString(minDuration));
        }
    }

    /**
     * Will only set properties after validating the input
     *
     * @param args {@link PomodoroCommand#getArgumentFormat()}
     * @throws BadUserInputException
     * @throws BadStateException
     */
    public void setFromArgs(String args) {
        if (args.isEmpty()) {
            return;
        }
        final Map<BooleanSetting, Boolean> booleanSettings = new HashMap<>();
        final List<Integer> numericArguments = new ArrayList<>();
        boolean intArgsEnd = false;
        for (String arg : args.split(" ")) {
            /*
             * Numerical arguments come first
             */
            if (!intArgsEnd) {
                try {
                    int parsedInt = Integer.parseInt(arg);
                    // First 3 arguments are durations
                    if (numericArguments.size() < 3) {
                        checkDuration(parsedInt);
                    }
                    else if (numericArguments.size() >= 4) {
                        throw new BadUserInputException("Arguments incorrect - too many numerical arguments");
                    }
                    numericArguments.add(parsedInt);
                }
                catch (NumberFormatException ignored) {
                    // End of numerical arguments
                    intArgsEnd = true;
                }
            }
            /*
             * Boolean settings come afterwards
             */
            if (intArgsEnd) {
                String[] currentArg = arg.split(":");
                if (currentArg.length != 2 || (!currentArg[1].equalsIgnoreCase("on") && !currentArg[1].equalsIgnoreCase("off"))) {
                    throw new BadUserInputException("Non-numerical arguments must be in the format 'ping:on' or 'ping:off'");
                }
                try {
                    BooleanSetting setting = BooleanSetting.valueOf(currentArg[0].toUpperCase());
                    booleanSettings.put(setting, currentArg[1].equalsIgnoreCase("on"));
                }
                catch (IllegalArgumentException e) {
                    throw new BadUserInputException("Unknown setting: " + currentArg[0]);
                }
            }
        }

        /*
         * Set settings
         */
        // Long break first as this will validate it (all others were validated in the parse loop)
        if (numericArguments.size() > 2) {
            setLongBreak(numericArguments.get(2), numericArguments.size() > 3 ? numericArguments.get(3) : null);
        }
        if (numericArguments.size() > 1) {
            this.states.get(SessionState.BREAK).setDuration(numericArguments.get(1));
        }
        if (numericArguments.size() > 0) {
            this.states.get(SessionState.WORK).setDuration(numericArguments.get(0));
        }
        for (Map.Entry<BooleanSetting, Boolean> setting : booleanSettings.entrySet()) {
            if (setting.getValue()) {
                this.booleanSettings.add(setting.getKey());
            }
            else {
                this.booleanSettings.remove(setting.getKey());
            }
        }
    }

    public void setLongBreak(Integer workSessionsBeforeLongBreak, Integer longBreakDuration) {
        if (workSessionsBeforeLongBreak == null && longBreakDuration == null) {
            this.workSessionsBeforeLongBreak = null;
            this.states.get(SessionState.LONG_BREAK).setDuration(null);
        }

        if (workSessionsBeforeLongBreak == null || longBreakDuration == null) {
            throw new BadUserInputException("Must provide long break duration AND work sessions until long break (or neither)");
        }
        if (workSessionsBeforeLongBreak < minWorkSessionsBeforeLongBreak) {
            throw new BadUserInputException("Minimum " + minWorkSessionsBeforeLongBreak + " work session before long break");
        }
        if (workSessionsBeforeLongBreak > maxWorkSessionsBeforeLongBreak) {
            throw new BadUserInputException("Maximum " + maxWorkSessionsBeforeLongBreak + " work session before long break");
        }
        checkDuration(longBreakDuration);
        this.workSessionsBeforeLongBreak = workSessionsBeforeLongBreak;
        this.states.get(SessionState.LONG_BREAK).setDuration(longBreakDuration);
    }

    /**
     * 0 durations will be set to null
     *
     * @throws BadStateException if workSessionsBeforeLongBreak is < 0
     */
    public void setWorkSessionsBeforeLongBreak(Integer workSessionsBeforeLongBreak) {
        if (workSessionsBeforeLongBreak != null) {
            if (workSessionsBeforeLongBreak == 0) {
                workSessionsBeforeLongBreak = null;
            }
            else if (workSessionsBeforeLongBreak < 0) {
                throw new BadStateException("Must have at least 1 work session before a break (0 to unset)");
            }
        }
        this.workSessionsBeforeLongBreak = workSessionsBeforeLongBreak;
    }

    public boolean getBooleanSetting(BooleanSetting setting) {
        return booleanSettings.contains(setting);
    }

    public Integer getWorkSessionsBeforeLongBreak() {
        return workSessionsBeforeLongBreak;
    }

    public Integer getStateDuration(SessionState state) {
        if (!state.isActiveState) {
            return timeoutDuration;
        }
        return states.get(state).duration;
    }

    public String getStateImage(SessionState state) {
        return states.get(state).image;
    }
}
