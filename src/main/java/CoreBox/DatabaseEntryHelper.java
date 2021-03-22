package CoreBox;

import ExceptionsBox.BadStateException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides useful functions for converting objects to and from the database's JSON format and the database in general
 */
public class DatabaseEntryHelper {
    /**
     * @return Map of the key that will be used in the database to a class that extends DatabaseEntryType
     */
    public static BiMap<Integer, Class> getAllTypes() {
        BiMap<Integer, Class> types = HashBiMap.create();
        types.put(0, PomodoroSettings.class);
        return types;
    }

    public static <T extends Enum<T>> Set<T> parseEnumArray(JsonArray array, Class<T> clazz) {
        Set<T> set = new HashSet<>();
        for (JsonElement element : array) {
            String settingString = element.getAsString();
            try {
                set.add(T.valueOf(clazz, settingString.toUpperCase()));
            }
            catch (IllegalArgumentException e) {
                throw new BadStateException("Json parse error: unknown " + clazz.getName() + " setting: " + settingString);
            }
        }
        return set;
    }

    public static Set<String> parseStringArray(JsonArray array) {
        Set<String> set = new HashSet<>();
        for (JsonElement element : array) {
            set.add(element.getAsString());
        }
        return set;
    }

    public static <T> JsonArray toJsonArray(Iterable<T> set) {
        JsonArray array = new JsonArray();
        for (T item : set) {
            array.add(item.toString());
        }
        return array;
    }
}
