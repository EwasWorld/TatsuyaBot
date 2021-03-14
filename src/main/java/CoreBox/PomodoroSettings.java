package CoreBox;

import java.awt.*;
import java.util.*;

import CoreBox.PomodoroSession.*;
import ExceptionsBox.BadUserInputException;
import net.dv8tion.jda.api.Permission;
import org.jetbrains.annotations.Nullable;

import static CoreBox.PomodoroSession.minutesToTimeString;

public class PomodoroSettings {
    public static final int maxDuration = 60 * 5;
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
//    int workDuration = 25;
//    int breakDuration = 10;
//    Integer longBreakDuration = null;
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
        int minDuration = 5;
        if (duration < minDuration) {
            throw new BadUserInputException("Minimum duration: " + minutesToTimeString(minDuration));
        }
    }
}
