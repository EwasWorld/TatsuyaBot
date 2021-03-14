package CoreBox;

import BotFrameworkBox.DatabaseEntryType;
import ExceptionsBox.BadStateException;
import com.google.gson.*;
import CoreBox.PomodoroSession.*;
import net.dv8tion.jda.api.Permission;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public enum DatabaseEntry implements DatabaseEntryType {
    POMODORO(1) {
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
                        JsonObject object = element.getAsJsonObject();
                        String stateString = object.get("state").getAsString();
                        SessionState state;
                        try {
                            state = SessionState.valueOf(stateString.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new BadStateException("Json parse error: unknown session state: " + stateString);
                        }
                        Color color = null;
                        String thumbnail = null;
                        Integer duration = null;
                        if (object.has("colour")) {
                            color = Color.decode(object.get("colour").getAsString());
                        }
                        if (object.has("thumbnail")) {
                            thumbnail = object.get("thumbnail").getAsString();
                        }
                        if (object.has("duration")) {
                            duration = object.get("duration").getAsInt();
                        }
                        pgi.addStateInfo(state, color, thumbnail, duration);
                    }
                }
                /*
                 * Sets
                 */
                if (main.has("booleanSettings")) {
                    Set<BooleanSetting> booleanSettings = parseEnumArray(main.getAsJsonArray("booleanSettings"), BooleanSetting.class);
                    if (!booleanSettings.isEmpty()) {
                        pgi.setBooleanSettings(booleanSettings);
                    }
                }
                if (main.has("bannedMembers")) {
                    Set<String> bannedMembers = parseStringArray(main.getAsJsonArray("bannedMembers"));
                    if (!bannedMembers.isEmpty()) {
                        pgi.setBannedMembers(bannedMembers);
                    }
                }
                if (main.has("adminPermissions")) {
                    Set<Permission> adminPermissions = parseEnumArray(main.getAsJsonArray("adminPermissions"), Permission.class);
                    if (!adminPermissions.isEmpty()) {
                        pgi.setAdminPermissions(adminPermissions);
                    }
                }
                /*
                 * Misc
                 */
                if (main.has("timeoutDuration")) {
                    pgi.setTimeoutDuration(main.get("timeoutDuration").getAsInt());
                }
                if (main.has("workSessionsBeforeLongBreak")) {
                    pgi.setWorkSessionsBeforeLongBreak(main.get("workSessionsBeforeLongBreak").getAsInt());
                }
                return pgi;
            };
        }

        @Override
        public JsonSerializer<PomodoroSettings> getSerializer() {
            return (src, typeOfSrc, context) -> {
                // TODO Serialiser
                // Color.decode(String.valueOf(Color.red.getRGB()));
                return new JsonObject();
            };
        }

        private <T extends Enum<T>> Set<T> parseEnumArray(JsonArray array, Class<T> clazz) {
            Set<T> set = new HashSet<>();
            for (JsonElement element : array) {
                String settingString = element.getAsString();
                try {
                    set.add(T.valueOf(clazz, settingString.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BadStateException("Json parse error: unknown " + clazz.getName() + " setting: " + settingString);
                }
            }
            return set;
        }

        private Set<String> parseStringArray(JsonArray array) {
            Set<String> set = new HashSet<>();
            for (JsonElement element : array) {
                set.add(element.getAsString());
            }
            return set;
        }
    };

    int key;

    DatabaseEntry(int key) {
        this.key = key;
    }

    @Override
    public int getKey() {
        return key;
    }
}
