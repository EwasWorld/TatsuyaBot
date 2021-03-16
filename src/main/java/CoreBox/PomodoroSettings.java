package CoreBox;

import java.awt.*;
import java.util.*;
import java.util.List;

import CoreBox.PomodoroSession.*;
import ExceptionsBox.BadStateException;
import ExceptionsBox.BadUserInputException;
import net.dv8tion.jda.api.Permission;
import org.jetbrains.annotations.Nullable;

import static CoreBox.PomodoroSession.minutesToTimeString;

public class PomodoroSettings {
    public static final int maxDuration = 60 * 5;
    public static final int minDuration = 5;
    private Map<SessionState, StateInfo> states = new HashMap<>() {
        {
            put(SessionState.WORK, new StateInfo(25, Color.BLUE, "https://img.jakpost.net/c/2020/03/01/2020_03_01_87874_1583031914.jpg"));
            put(SessionState.BREAK, new StateInfo(10, Color.CYAN, "https://img.webmd.com/dtmcms/live/webmd/consumer_assets/site_images/article_thumbnails/slideshows/stretches_to_help_you_get_loose_slideshow/1800x1200_stretches_to_help_you_get_loose_slideshow.jpg"));
            put(SessionState.LONG_BREAK, new StateInfo(null, Color.CYAN, "https://miro.medium.com/max/10000/1*BbmQbf-ZHVIgBaoUVShq6g.jpeg"));
            put(SessionState.NOT_STARTED, new StateInfo(null,  Color.ORANGE, "https://wp-media.labs.com/wp-content/uploads/2019/01/01140607/How-to-De-Clutter-Your-Workspace1.jpg"));
            put(SessionState.PAUSED, new StateInfo(null,  Color.ORANGE, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT-zcKdGFYy2oPkxzqj0lXhGYDyLofR-c083Q&usqp=CAU"));
            put(SessionState.FINISHED, new StateInfo(null, null, "https://static01.nyt.com/images/2015/11/03/health/well_lyingdown/well_lyingdown-tmagArticle.jpg"));
        }
    };

    private Set<BooleanSetting> booleanSettings = new HashSet<>() {
        {
            add(BooleanSetting.PINGS);
            add(BooleanSetting.AUTO);
            add(BooleanSetting.DELETE);
        }
    };
    private Integer workSessionsBeforeLongBreak = null;
    private int timeoutDuration = 60;
    private Set<String> bannedMembers = new HashSet<>();
    // member.hasPermission(Permission.MANAGE_SERVER, Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL);
    private Set<Permission> adminPermissions = new HashSet<>();

    private class StateInfo {
        Integer duration = null;
        Color colour = null;
        String thumbnail = null;

        public StateInfo() {
        }

        public StateInfo(Integer duration, Color colour, String thumbnail) {
            this.duration = duration;
            this.colour = colour;
            this.thumbnail = thumbnail;
        }
    }

    public void addStateInfo(SessionState state, @Nullable Color color, @Nullable String thumbnail, @Nullable Integer duration) {
        StateInfo info = getInfo(state);
        if (color != null) {
            info.colour = color;
        }
        if (thumbnail != null) {
            info.thumbnail = thumbnail;
        }
        if (duration != null && state.isStarted) {
            info.duration = duration;
        }
    }

    public void setStateDuration(SessionState state, Integer duration) {
        if (duration == null && (state == SessionState.WORK || state == SessionState.BREAK)) {
            throw new BadUserInputException("Cannot have a null duration on work or break");
        }
        StateInfo info = getInfo(state);
        info.duration = duration;
    }

    private StateInfo getInfo(SessionState state) {
        if (states.containsKey(state)) {
            return states.get(state);
        } else {
            return new StateInfo();
        }
    }

    public void setBooleanSettings(Set<BooleanSetting> booleanSettings) {
        this.booleanSettings = booleanSettings;
    }

    public void setWorkSessionsBeforeLongBreak(Integer workSessionsBeforeLongBreak) {
        this.workSessionsBeforeLongBreak = workSessionsBeforeLongBreak;
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

    public void checkDuration(Integer duration) {
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

    public void setFromArgs(String args) {
        Map<BooleanSetting, Boolean> booleanSettings = null;
        if (args.isEmpty()) {
            return;
        }
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
            checkDuration(workDuration);
        }
        if (argsInts.size() > 1) {
            breakDuration = argsInts.get(1);
            checkDuration(breakDuration);
        }
        if (argsInts.size() > 2) {
            longBreakDuration = argsInts.get(2);
            checkDuration(longBreakDuration);
        }
        if (argsInts.size() > 3) {
            sessionsBeforeLongBreakDuration = argsInts.get(3);
        }

        /*
         * Set settings
         */
        setLongBreak(longBreakDuration, sessionsBeforeLongBreakDuration);
        if (workDuration != null) {
            this.states.get(SessionState.WORK).duration = workDuration;
        }
        if (breakDuration != null) {
            this.states.get(SessionState.BREAK).duration = breakDuration;
        }
        if (booleanSettings != null) {
            setBooleanSettings(booleanSettings);
        }
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
        checkDuration(longBreakDuration);
        this.workSessionsBeforeLongBreak = workSessionsBeforeLongBreak;
        this.states.get(SessionState.LONG_BREAK).duration = longBreakDuration;
    }

    public boolean hasSetting(BooleanSetting setting) {
        return booleanSettings.contains(setting);
    }

    public Integer getWorkSessionsBeforeLongBreak() {
        return workSessionsBeforeLongBreak;
    }

    public Integer getStateDuration(SessionState state) {
        if (!state.isStarted) {
            return timeoutDuration;
        }
        return states.get(state).duration;
    }
}
