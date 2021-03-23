package CoreBox;

import BotFrameworkBox.DatabaseEntryType;
import CoreBox.PomodoroSession.BooleanSetting;
import CoreBox.PomodoroSession.SessionState;
import ExceptionsBox.BadStateException;
import ExceptionsBox.BadUserInputException;
import TatsuyaCommands.PomodoroCommand;
import com.google.gson.*;
import net.dv8tion.jda.api.Permission;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

import static CoreBox.PomodoroSession.minutesToDisplayString;

/**
 * Settings such as work/study split for a particular pomodoro session
 */
public class PomodoroSettings implements DatabaseEntryType<PomodoroSettings> {
    /*
     * Defaults
     */
    /**
     * Maximum allowed time input in minutes
     */
    public static final int maxDuration = 60 * 5;
    /**
     * Minimum allowed time input in minutes
     */
    public static final int minDuration = 5;
    public static final int maxWorkSessionsBeforeLongBreak = 30;
    public static final int minWorkSessionsBeforeLongBreak = 1;
    public static final String boolSettingDeliminator = ":";
    public static final String boolSettingOn = "on";
    public static final String boolSettingOff = "off";
    private static final Map<SessionState, StateInfo> defaultStates = new HashMap<>() {
        {
            put(SessionState.WORK,
                    new StateInfo(SessionState.WORK, 25, Color.BLUE, SessionState.WORK.getDefaultImage())
            );
            put(SessionState.BREAK,
                    new StateInfo(SessionState.BREAK, 10, Color.CYAN, SessionState.BREAK.getDefaultImage())
            );
            put(SessionState.LONG_BREAK,
                    new StateInfo(SessionState.LONG_BREAK, null, Color.CYAN, SessionState.LONG_BREAK.getDefaultImage())
            );
            put(SessionState.NOT_STARTED, new StateInfo(SessionState.NOT_STARTED, null, Color.ORANGE,
                    SessionState.NOT_STARTED.getDefaultImage()
            ));
            put(SessionState.PAUSED,
                    new StateInfo(SessionState.PAUSED, null, Color.ORANGE, SessionState.PAUSED.getDefaultImage())
            );
            put(SessionState.FINISHED,
                    new StateInfo(SessionState.FINISHED, null, null, SessionState.FINISHED.getDefaultImage())
            );
        }
    };
    private static final Set<Permission> defaultAdminPermissions = new HashSet<>() {
        {
            add(Permission.MANAGE_SERVER);
            add(Permission.ADMINISTRATOR);
            add(Permission.MANAGE_CHANNEL);
        }
    };
    private static final int defaultTimeoutDuration = 60;
    private static final String defaultDateFormat = "dd/MM/yyyy";
    private static final String defaultTimeFormat = "HH:mm";
    private final Set<BooleanSetting> defaultBooleanSettings = new HashSet<>() {
        {
            add(BooleanSetting.PINGS);
            add(BooleanSetting.AUTO);
            add(BooleanSetting.DELETE);
        }
    };
    /*
     * Settings
     */
    private final Map<SessionState, StateInfo> states = new HashMap<>(defaultStates);

    /**
     * Presence in the map indicates the setting is on
     */
    private Set<BooleanSetting> booleanSettings = new HashSet<>(defaultBooleanSettings);
    /**
     * This setting also dictates whether the long break function will be used
     */
    private Integer workSessionsBeforeLongBreak = null;
    /**
     * For the non-active states, the pomodoro session will be cancelled after this amount of inactivity
     */
    private int timeoutDuration = 60;
    private String dateFormat = "dd/MM/yyyy";
    private String timeFormat = "HH:mm";
    private Set<String> bannedMembers = new HashSet<>();
    private Set<Permission> adminPermissions = new HashSet<>(defaultAdminPermissions);

    public PomodoroSettings() { }

    /**
     * Check that non-null durations come between the {@link #maxDuration} and {@link #minDuration}
     *
     * @throws BadUserInputException if outside of bounds
     */
    public static void checkDuration(Integer duration) {
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
     * @throws BadUserInputException if args is invalid
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
                String[] currentArg = arg.split(boolSettingDeliminator);
                if (currentArg.length != 2 || (!currentArg[1].equalsIgnoreCase(boolSettingOn) && !currentArg[1]
                        .equalsIgnoreCase(boolSettingOff))) {
                    throw new BadUserInputException(
                            String.format("Non-numerical arguments must be in the format 'ping%s%s' or 'ping%s%s'",
                                    boolSettingDeliminator, boolSettingOn, boolSettingDeliminator, boolSettingOff
                            ));
                }
                try {
                    BooleanSetting setting = BooleanSetting.valueOf(currentArg[0].toUpperCase());
                    booleanSettings.put(setting, currentArg[1].equalsIgnoreCase(boolSettingOn));
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
            setLongBreak(numericArguments.size() > 3 ? numericArguments.get(3) : null, numericArguments.get(2));
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
            throw new BadUserInputException(
                    "Must provide long break duration AND work sessions until long break (or neither)");
        }
        if (workSessionsBeforeLongBreak < minWorkSessionsBeforeLongBreak) {
            throw new BadUserInputException(
                    "Minimum " + minWorkSessionsBeforeLongBreak + " work session before long break");
        }
        if (workSessionsBeforeLongBreak > maxWorkSessionsBeforeLongBreak) {
            throw new BadUserInputException(
                    "Maximum " + maxWorkSessionsBeforeLongBreak + " work session before long break");
        }
        checkDuration(longBreakDuration);
        this.workSessionsBeforeLongBreak = workSessionsBeforeLongBreak;
        this.states.get(SessionState.LONG_BREAK).setDuration(longBreakDuration);
    }

    public boolean getBooleanSetting(BooleanSetting setting) {
        return booleanSettings.contains(setting);
    }

    public Integer getWorkSessionsBeforeLongBreak() {
        return workSessionsBeforeLongBreak;
    }

    public Integer getStateDuration(SessionState state) {
        if (!state.isActiveState()) {
            return timeoutDuration;
        }
        return states.get(state).duration;
    }

    public String getStateImage(SessionState state) {
        return states.get(state).image;
    }

    public DateTimeFormatter getDateTimeFormatter() {
        return DateTimeFormatter
                .ofPattern((booleanSettings.contains(BooleanSetting.DATE) ? dateFormat + " " : "") + timeFormat + " z");
    }

    @Override
    public Class<PomodoroSettings> getReturnClass() {
        return PomodoroSettings.class;
    }

    @Override
    public JsonDeserializer<PomodoroSettings> getDeserializer() {
        return (json, typeOfT, context) -> {
            PomodoroSettings pgi = new PomodoroSettings();
            JsonObject main = json.getAsJsonObject();

            /*
             * States
             */
            if (main.has("states")) {
                for (JsonElement element : main.getAsJsonArray("states")) {
                    StateInfo readInfo = StateInfo.deserialise(element.getAsJsonObject());
                    pgi.states.get(readInfo.state).overlay(readInfo);
                }
            }

            /*
             * Sets
             */
            if (main.has("booleanSettings")) {
                booleanSettings = DatabaseEntryHelper
                        .parseEnumArray(main.getAsJsonArray("booleanSettings"), BooleanSetting.class);
            }
            if (main.has("bannedMembers")) {
                bannedMembers = DatabaseEntryHelper.parseStringArray(main.getAsJsonArray("bannedMembers"));
            }
            if (main.has("adminPermissions")) {
                adminPermissions = DatabaseEntryHelper
                        .parseEnumArray(main.getAsJsonArray("adminPermissions"), Permission.class);
            }

            /*
             * Misc
             */
            if (main.has("timeoutDuration")) {
                int timeout = main.get("timeoutDuration").getAsInt();
                checkDuration(timeout);
                pgi.timeoutDuration = timeout;
            }
            if (main.has("workSessionsBeforeLongBreak")) {
                pgi.workSessionsBeforeLongBreak = main.get("workSessionsBeforeLongBreak").getAsInt();
            }
            if (main.has("dateFormat")) {
                pgi.dateFormat = main.get("dateFormat").getAsString();
            }
            if (main.has("timeFormat")) {
                pgi.timeFormat = main.get("timeFormat").getAsString();
            }

            /*
             * Verify object
             */
            // Throws an error if this combination is invalid
            setLongBreak(pgi.getWorkSessionsBeforeLongBreak(), pgi.states.get(SessionState.LONG_BREAK).duration);

            return pgi;
        };
    }

    @Override
    public JsonSerializer<PomodoroSettings> getSerializer() {
        return (src, typeOfSrc, context) -> {
            JsonObject main = new JsonObject();

            /*
             * States
             */
            JsonArray statesJson = new JsonArray();
            for (StateInfo info : states.values()) {
                if (!info.equals(defaultStates.get(info.state))) {
                    statesJson.add(info.serialize());
                }
            }
            if (statesJson.size() > 0) {
                main.add("states", statesJson);
            }

            /*
             * Sets
             */
            if (!booleanSettings.equals(defaultBooleanSettings)) {
                main.add("booleanSettings", DatabaseEntryHelper.toJsonArray(booleanSettings));
            }
            if (!bannedMembers.isEmpty()) {
                main.add("bannedMembers", DatabaseEntryHelper.toJsonArray(bannedMembers));
            }
            if (!adminPermissions.equals(defaultAdminPermissions)) {
                main.add("adminPermissions", DatabaseEntryHelper.toJsonArray(adminPermissions));
            }

            /*
             * Misc
             */
            if (timeoutDuration != defaultTimeoutDuration) {
                main.addProperty("timeoutDuration", timeoutDuration);
            }
            if (workSessionsBeforeLongBreak != null) {
                main.addProperty("workSessionsBeforeLongBreak", workSessionsBeforeLongBreak);
            }
            if (!dateFormat.equals(defaultDateFormat)) {
                main.addProperty("dateFormat", dateFormat);
            }
            if (!timeFormat.equals(defaultTimeFormat)) {
                main.addProperty("timeFormat", timeFormat);
            }

            return main;
        };
    }

    /**
     * Store information which the clan can change about each state
     */
    private static class StateInfo {
        private final SessionState state;
        Color colour;
        String image;
        private Integer duration;

        private StateInfo(SessionState state) {
            this.state = state;
            this.duration = null;
            this.colour = null;
            this.image = null;
        }

        public StateInfo(SessionState state, Integer duration, Color colour, String image) {
            this.state = state;
            //noinspection ConstantConditions it will be null when generating default states
            StateInfo defaultInfo = defaultStates != null ? defaultStates.get(state) : null;
            this.duration = duration != null ? duration : (defaultInfo != null ? defaultInfo.duration : null);
            this.colour = colour != null ? colour : (defaultInfo != null ? defaultInfo.colour : null);
            this.image = image != null ? image : (defaultInfo != null ? defaultInfo.image : null);
        }

        public static StateInfo deserialise(JsonObject object) {
            String stateString = object.get("state").getAsString();
            StateInfo info;
            try {
                info = new StateInfo(SessionState.valueOf(stateString.toUpperCase()));
            }
            catch (IllegalArgumentException e) {
                throw new BadStateException("Json parse error: unknown session state: " + stateString);
            }
            if (object.has("colour")) {
                info.colour = Color.decode(object.get("colour").getAsString());
            }
            if (object.has("image")) {
                info.image = object.get("image").getAsString();
            }
            if (object.has("duration")) {
                info.duration = object.get("duration").getAsInt();
            }
            return info;
        }

        /**
         * @throws BadStateException if state type is not active or for null values being assigned to WORK or
         *         BREAK states
         */
        public void setDuration(Integer duration) {
            if (!state.isActiveState()) {
                throw new BadStateException("Suspended states cannot have a duration");
            }
            if (duration == null && (state == SessionState.WORK || state == SessionState.BREAK)) {
                throw new BadStateException("Cannot have a null duration on work or break");
            }
            checkDuration(duration);
            this.duration = duration;
        }

        public JsonElement serialize() {
            JsonObject main = new JsonObject();
            main.addProperty("state", state.toString());
            if (duration != null) {
                main.addProperty("colour", duration);
            }
            if (image != null) {
                main.addProperty("image", image);
            }
            if (duration != null) {
                main.addProperty("duration", duration);
            }
            return main;
        }

        /**
         * Overlay the current object with the non-null elements of info
         */
        public void overlay(StateInfo info) {
            if (info.image != null) {
                image = info.image;
            }
            if (info.colour != null) {
                colour = info.colour;
            }
            if (info.duration != null) {
                setDuration(info.duration);
            }
        }
    }
}
