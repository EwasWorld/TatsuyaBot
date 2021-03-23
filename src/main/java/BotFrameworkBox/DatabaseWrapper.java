package BotFrameworkBox;

import ExceptionsBox.BadStateException;
import ExceptionsBox.ContactEwaException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.Map;

public class DatabaseWrapper {
    /**
     * Maps the key that will be used in the database to a class that extends DatabaseEntryType Prevents duplicate keys
     * as keys are user-defined
     */
    private static final BiMap<Integer, Class> databaseEntryTypes = HashBiMap.create();

    /*
     * Database connection
     */
    private static final String databaseFileLocation = "Tatsuya.db";
    private static final String sqlPrefix = "jdbc:sqlite:";
    // Used to establish the connection to the database
    private static final String urlPrefix = sqlPrefix + Bot.getPathToTatsuyaBot();
    private static final String tableName = "blobs";
    private static String url = urlPrefix + databaseFileLocation;
    private static Connection connection = null;

    /**
     * Validates all types then stores them
     */
    public static void setDatabaseEntryTypes(BiMap<Integer, Class> databaseEntryTypes) {
        for (Map.Entry<Integer, Class> entry : databaseEntryTypes.entrySet()) {
            final Class clazz = entry.getValue();
            if (clazz.isAnonymousClass()) {
                throw new BadStateException(clazz.getName() + ": anonymous not allowed");
            }
            if (!DatabaseEntryType.class.isAssignableFrom(clazz)) {
                throw new BadStateException(clazz.getName() + " does not extend " + DatabaseEntryType.class.getName());
            }
            if (!Modifier.isPublic(clazz.getModifiers())) {
                throw new BadStateException(clazz.getName() + " must be public");
            }
            try {
                // An empty constructor is required to instantiate a simple object which can then provide the
                //      deserializer
                // TODO Is there a way to not require an empty constructor?
                //noinspection unchecked
                Constructor constructor = clazz.getDeclaredConstructor();
                if (!Modifier.isPublic(constructor.getModifiers())) {
                    throw new BadStateException(
                            "default (no-parameter) constructor of " + clazz.getName() + " must be public"
                    );
                }
            }
            catch (Exception e) {
                throw new BadStateException(clazz.getName() + " does not have a default (no-parameter) constructor");
            }
            //noinspection unchecked: type is checked above
            DatabaseEntryType instantiation = instantiate(clazz);
            if (instantiation.getSerializer() == null) {
                throw new BadStateException(clazz.getName() + " getSerializer returns null");
            }
            if (instantiation.getDeserializer() == null) {
                throw new BadStateException(clazz.getName() + " getDeserializer returns null");
            }
            DatabaseWrapper.databaseEntryTypes.put(entry.getKey(), clazz);
        }
    }

    /**
     * Open a connection to the database, populating {@link #connection}, and create the table if it doesn't already
     * exist
     *
     * @throws ContactEwaException if connection cannot be established
     */
    private static void getConnectionAndInitDb() {
        try {
            if (connection == null || connection.isClosed()) {
                /*
                 * Fetches the existing database or creates a new one
                 */
                connection = DriverManager.getConnection(url);
                if (!new File(databaseFileLocation).exists()) {
                    connection.getMetaData();
                }
            }
        }
        catch (SQLException e) {
            throw new ContactEwaException("Database connection error");
        }

        /*
         * Create table
         */
        try (Statement stmt = connection.createStatement()) {
            stmt.execute((
                    "CREATE TABLE IF NOT EXISTS <TBL>"
                            + " ("
                            + "guildId text NOT NULL,"
                            + "entryType int NOT NULL,"
                            + "entry text NOT NULL,"
                            + "CONSTRAINT PK_<TBL> PRIMARY KEY(guildId, entryType)"
                            + ");"
            ).replaceAll("<TBL>", tableName));
        }
        catch (SQLException e) {
            throw new ContactEwaException("Table creation error");
        }
    }

    public static <A extends DatabaseEntryType, T extends DatabaseEntryType> A getData(String guild, Class<T> type) {
        Class<A> resolvedClass = checkAndResolveClass(type);
        getConnectionAndInitDb();

        String returnedJson;
        String sql = "SELECT * FROM " + tableName + " WHERE guildId = ? AND entryType = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, guild);
            ps.setInt(2, databaseEntryTypes.inverse().get(resolvedClass));
            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            returnedJson = rs.getString("entry");
            if (rs.next()) {
                throw new BadStateException("Database entry not unique");
            }
        }
        catch (SQLException e) {
            throw new BadStateException("Database query failed");
        }

        return getGson(resolvedClass, instantiate(resolvedClass).getDeserializer()).fromJson(returnedJson,
                resolvedClass
        );
    }

    /**
     * Will overwrite the existing entry in the database for guild/dataType if it exists
     */
    public static <T, A extends DatabaseEntryType> void saveData(String guild, DatabaseEntryType<T> data) {
        Class<A> resolvedClass = checkAndResolveClass(data.getClass());
        getConnectionAndInitDb();

        A typeCheckedData = resolvedClass.cast(data);
        String json = getGson(resolvedClass, typeCheckedData.getSerializer()).toJson(typeCheckedData, resolvedClass);
        String sql = "REPLACE INTO " + tableName + " (guildId, entryType, entry) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, guild);
            ps.setInt(2, databaseEntryTypes.inverse().get(resolvedClass));
            ps.setString(3, json);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new BadStateException("Database query failed " + e.getMessage());
        }
    }

    /**
     * Ensures that the provided class is in {@link #databaseEntryTypes} Uses the immediate superclass if the provided
     * class is anonymous
     */
    public static <A extends DatabaseEntryType, T extends DatabaseEntryType> Class<A> checkAndResolveClass(
            Class<T> clazz
    ) {
        if (clazz == null) {
            throw new IllegalArgumentException("Cannot be null");
        }

        Class returnClass = null;
        if (databaseEntryTypes.containsValue(clazz)) {
            returnClass = clazz;
        }
        else if (clazz.isAnonymousClass() && databaseEntryTypes.containsValue(clazz.getSuperclass())) {
            returnClass = clazz.getSuperclass();
        }
        if (returnClass == null || !DatabaseEntryType.class.isAssignableFrom(returnClass)) {
            throw new BadStateException(clazz.getName()
                    + " not recognised as a database entry type, make sure it's set with setDatabaseEntryTypes");
        }
        //noinspection unchecked: Type is checked in above if statement
        return returnClass;
    }

    private static <T extends DatabaseEntryType> T instantiate(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        }
        catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new ContactEwaException("No default constructor for " + DatabaseEntryType.class.getName());
        }
    }

    /**
     * @param type the type which will be converted from/to
     * @param typeAdapter either a JsonSerializer or a JsonDeserializer
     */
    private static <T> Gson getGson(Class<T> type, Object typeAdapter) {
        if (!(typeAdapter instanceof JsonSerializer || typeAdapter instanceof JsonDeserializer)) {
            throw new BadStateException("TypeAdapter is incorrect type");
        }
        return new GsonBuilder().registerTypeAdapter(type, typeAdapter).create();
    }

    public static boolean deleteDatabase() {
        if (!isInTestMode()) {
            throw new IllegalStateException("Cannot delete database unless in test mode.");
        }
        closeConnection();
        File dbFile = new File(url.substring(sqlPrefix.length()));
        return !dbFile.exists() || dbFile.delete();
    }

    /**
     * Change the database mode so that testing doesn't affect live data
     */
    public static void setTestMode() {
        url = urlPrefix + "Test" + databaseFileLocation;
    }

    /**
     * Used as a sanity check
     */
    public static boolean isInTestMode() {
        return !url.equals(urlPrefix + databaseFileLocation);
    }

    /**
     * @throws ContactEwaException if an SQLException occurs
     */
    private static void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
            connection = null;
        }
        catch (SQLException e) {
            throw new ContactEwaException("Close connection error");
        }
    }
}
