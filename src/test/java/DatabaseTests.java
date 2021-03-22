import BotFrameworkBox.DatabaseEntryType;
import BotFrameworkBox.DatabaseWrapper;
import CoreBox.DatabaseEntryHelper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("ConstantConditions")
public class DatabaseTests {
    public static final String guild = "GuildId";

    @BeforeEach
    public void setup() {
        DatabaseWrapper.setTestMode();
        BiMap<Integer, Class> databaseEntryTypes = HashBiMap.create();
        databaseEntryTypes.put(1, TestHelperObject.class);
        DatabaseWrapper.setDatabaseEntryTypes(databaseEntryTypes);
    }

    /**
     * Simple serialize/store then retrieve/deserialize an object, ensuring it is preserved
     */
    @Test
    public void simpleReadWrite() {
        TestHelperObject item = new TestHelperObject();
        item.integerItem = 20;
        item.stringItem = "TestData";
        item.setItem = new HashSet<>() {
            {
                add("Item1");
                add("Item2");
            }
        };
        DatabaseWrapper.saveData(guild, item);

        TestHelperObject emptyItem = new TestHelperObject();
        TestHelperObject retrievedItem = DatabaseWrapper.getData(guild, emptyItem);
        Assertions.assertEquals(item.integerItem, retrievedItem.integerItem);
        Assertions.assertEquals(item.stringItem, retrievedItem.stringItem);
        Assertions.assertEquals(item.setItem, retrievedItem.setItem);
    }

    /**
     * Skeleton class that can be written to and from the database
     */
    private static class TestHelperObject implements DatabaseEntryType<TestHelperObject> {
        Integer integerItem = null;
        String stringItem = null;
        Set<String> setItem = null;

        public TestHelperObject() {
        }

        @Override
        public Class<TestHelperObject> getReturnClass() {
            return TestHelperObject.class;
        }

        @Override
        public JsonDeserializer<TestHelperObject> getDeserializer() {
            return (json, typeOfT, context) -> {
                TestHelperObject dti = new TestHelperObject();
                JsonObject main = json.getAsJsonObject();
                if (main.has("integerItem")) {
                    dti.integerItem = main.get("integerItem").getAsInt();
                }
                if (main.has("stringItem")) {
                    dti.stringItem = main.get("stringItem").getAsString();
                }
                if (main.has("setItem")) {
                    dti.setItem = DatabaseEntryHelper.parseStringArray(main.get("setItem").getAsJsonArray());
                }
                return dti;
            };
        }

        @Override
        public JsonSerializer<TestHelperObject> getSerializer() {
            return (src, typeOfSrc, context) -> {
                JsonObject main = new JsonObject();
                if (src.integerItem != null) main.addProperty("integerItem", src.integerItem);
                if (src.stringItem != null) main.addProperty("stringItem", src.stringItem);
                if (src.setItem != null) main.add("setItem", DatabaseEntryHelper.toJsonArray(src.setItem));
                return main;
            };
        }
    }
}
