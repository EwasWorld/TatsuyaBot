import BotFrameworkBox.DatabaseEntryType;
import BotFrameworkBox.DatabaseWrapper;
import CoreBox.DatabaseEntryHelper;
import ExceptionsBox.BadStateException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("ConstantConditions")
public class DatabaseTests {
    public static final String guild = "GuildId";
    public static final TestHelperObjects.WellFormed testData2 = new TestHelperObjects.WellFormed(
            35,
            "2 TestData 2",
            new HashSet<>() {
                {
                    add("2 Item1 2");
                    add("2 Item2 2");
                }
            }
    );
    /*
     * Interestingly, the format `new WellFormed() {{integerItem = 20}}` will result in the type being DatabaseTests$1,
     *   since it seems to take it as an anonymous definition
     */
    private static final TestHelperObjects.WellFormed testData1 = new TestHelperObjects.WellFormed(
            20,
            "TestData",
            new HashSet<>() {
                {
                    add("Item1");
                    add("Item2");
                }
            }
    );
    private static BiMap<Integer, Class> databaseEntryTypes;
    public DatabaseTests() { }

    @BeforeEach
    public void setup() {
        DatabaseWrapper.setTestMode();
        databaseEntryTypes = HashBiMap.create();
        databaseEntryTypes.put(1, TestHelperObjects.WellFormed.class);
        // Reserved key: 99 for bad data
        DatabaseWrapper.setDatabaseEntryTypes(databaseEntryTypes);
    }

    @AfterEach
    public void tearDown() {
        Assertions.assertTrue(DatabaseWrapper.deleteDatabase());
    }

    /**
     * Simple serialize/store then retrieve/deserialize of two objects object, ensuring it is preserved
     */
    @Test
    public void simpleReadWrite() {
        String guild2 = guild + "differentGuild";
        DatabaseWrapper.saveData(guild, testData1);
        Assertions.assertEquals(testData1, DatabaseWrapper.getData(guild, TestHelperObjects.WellFormed.class));
        DatabaseWrapper.saveData(guild2, testData2);
        Assertions.assertEquals(testData1, DatabaseWrapper.getData(guild, TestHelperObjects.WellFormed.class));
        Assertions.assertEquals(testData2, DatabaseWrapper.getData(guild2, TestHelperObjects.WellFormed.class));
    }

    /**
     * Ensure an anonymous instance of a DatabaseEntryType is accepted
     */
    @Test
    public void anonymousClass() {
        TestHelperObjects.WellFormed testData = new TestHelperObjects.WellFormed() {
            {
                integerItem = 100;
                stringItem = "String data";
                setItem = new HashSet<>() {
                    {
                        add("String1");
                        add("String2");
                    }
                };
            }
        };

        DatabaseWrapper.saveData(guild, testData);
        Assertions.assertEquals(testData, DatabaseWrapper.getData(guild, TestHelperObjects.WellFormed.class));
    }

    /**
     * Ensure that if the same type is written to the database from the same guild, the original data is overwritten
     */
    @Test
    public void overwriteData() {
        DatabaseWrapper.saveData(guild, testData1);
        DatabaseWrapper.saveData(guild, testData2);
        Assertions.assertEquals(testData2, DatabaseWrapper.getData(guild, TestHelperObjects.WellFormed.class));
    }

    /**
     * Check that a non-public class is rejected on database wrapper setup
     */
    @Test
    public void nonPublicClass() {
        databaseEntryTypes.put(99, TestHelperObjects.PackagePrivateClass.class);
        Assertions
                .assertThrows(BadStateException.class, () -> DatabaseWrapper.setDatabaseEntryTypes(databaseEntryTypes));
    }

    /**
     * Check that a class without a default constructor is rejected on database wrapper setup
     */
    @Test
    public void noDefaultConstructor() {
        databaseEntryTypes.put(99, TestHelperObjects.NoDefaultConstructor.class);
        Assertions
                .assertThrows(BadStateException.class, () -> DatabaseWrapper.setDatabaseEntryTypes(databaseEntryTypes));
    }

    /**
     * Check that a class without a default constructor is rejected on database wrapper setup
     */
    @Test
    public void privateConstructor() {
        databaseEntryTypes.put(99, TestHelperObjects.PrivateConstructor.class);
        Assertions
                .assertThrows(BadStateException.class, () -> DatabaseWrapper.setDatabaseEntryTypes(databaseEntryTypes));
    }

    /**
     * Check that a class that doesn't implement DatabaseEntryType is rejected on database wrapper setup
     */
    @Test
    public void doesNotImplement() {
        databaseEntryTypes.put(99, TestHelperObjects.DoesNotImplement.class);
        Assertions
                .assertThrows(BadStateException.class, () -> DatabaseWrapper.setDatabaseEntryTypes(databaseEntryTypes));
    }

    /**
     * Skeleton classes that can be written to and from the database or used to trigger certain errors
     */
    public static class TestHelperObjects {
        /**
         * Required to make class instantiations and class names correct https://stackoverflow
         * .com/questions/17006585/why-does-classname1-class-generate-in-this-situation/17006628
         */
        public TestHelperObjects() { }

        public static class WellFormed implements DatabaseEntryType<WellFormed> {
            Integer integerItem = null;
            String stringItem = null;
            Set<String> setItem = null;

            public WellFormed() { }

            public WellFormed(Integer integerItem, String stringItem, Set<String> setItem) {
                this.integerItem = integerItem;
                this.stringItem = stringItem;
                this.setItem = setItem;
            }

            @Override
            public Class<WellFormed> getReturnClass() {
                return WellFormed.class;
            }

            @Override
            public JsonDeserializer<WellFormed> getDeserializer() {
                return (json, typeOfT, context) -> {
                    WellFormed dti = new WellFormed();
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
            public JsonSerializer<WellFormed> getSerializer() {
                return (src, typeOfSrc, context) -> {
                    JsonObject main = new JsonObject();
                    if (src.integerItem != null) main.addProperty("integerItem", src.integerItem);
                    if (src.stringItem != null) main.addProperty("stringItem", src.stringItem);
                    if (src.setItem != null) main.add("setItem", DatabaseEntryHelper.toJsonArray(src.setItem));
                    return main;
                };
            }

            /**
             * {@inheritDoc} Allows objects to be compared using assertEquals too
             */
            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof WellFormed)) {
                    return super.equals(obj);
                }
                WellFormed a = (WellFormed) obj;
                return integerItem.equals(a.integerItem) && stringItem.equals(a.stringItem) && setItem
                        .equals(a.setItem);
            }
        }

        static class PackagePrivateClass implements DatabaseEntryType<PackagePrivateClass> {
            public PackagePrivateClass() { }

            @Override
            public Class<PackagePrivateClass> getReturnClass() {
                return null;
            }

            @Override
            public JsonDeserializer<PackagePrivateClass> getDeserializer() {
                return null;
            }

            @Override
            public JsonSerializer<PackagePrivateClass> getSerializer() {
                return null;
            }
        }

        public static class NoDefaultConstructor implements DatabaseEntryType<NoDefaultConstructor> {
            NoDefaultConstructor() { }

            @Override
            public Class<NoDefaultConstructor> getReturnClass() {
                return null;
            }

            @Override
            public JsonDeserializer<NoDefaultConstructor> getDeserializer() {
                return null;
            }

            @Override
            public JsonSerializer<NoDefaultConstructor> getSerializer() {
                return null;
            }
        }

        public static class PrivateConstructor implements DatabaseEntryType<PrivateConstructor> {
            PrivateConstructor() { }

            @Override
            public Class<PrivateConstructor> getReturnClass() {
                return null;
            }

            @Override
            public JsonDeserializer<PrivateConstructor> getDeserializer() {
                return null;
            }

            @Override
            public JsonSerializer<PrivateConstructor> getSerializer() {
                return null;
            }
        }

        public static class DoesNotImplement {
            public DoesNotImplement() { }
        }
    }
}
