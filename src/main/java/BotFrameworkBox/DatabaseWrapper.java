package BotFrameworkBox;

import ExceptionsBox.BadStateException;
import ExceptionsBox.ContactEwaException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DatabaseWrapper {
    private static Set<DatabaseEntryType> databaseEntryTypes = null;

    /*
     * Database connection
     */
    private static final String databaseFileLocation = "Tatsuya.db";
    // Used to establish the connection to the database
    private static final String urlPrefix = "jdbc:sqlite:" + Bot.getPathToTatsuyaBot();
    private static String url = urlPrefix + databaseFileLocation;
    private static final String tableName = "blobs";
    private static Connection connection = null;

    public static void setDatabaseEntryTypes(DatabaseEntryType[] databaseEntryTypes) {
        Set<Integer> keys = new HashSet<>();
        for (DatabaseEntryType type : databaseEntryTypes) {
            if (!type.getReturnClass().isAssignableFrom(JsonDeserializer.class)) {
                throw new BadStateException("Database entry types must extend DatabaseItem");
            }
            if (keys.contains(type.getKey())) {
                throw new BadStateException("Duplicate key: " + type.getKey());
            }
            keys.add(type.getKey());
        }
        DatabaseWrapper.databaseEntryTypes = new HashSet<>(Arrays.asList(databaseEntryTypes));
    }

    /**
     * Open a connection to the database, populating {@link #connection}, and create the table if it doesn't already
     * exist
     *
     * @throws ContactEwaException if connection cannot be established
     */
    private static void getConnection() {
        if (connection == null) {
            /*
             * Fetches the existing database or creates a new one
             */
            try {
                connection = DriverManager.getConnection(url);
                if (!new File(databaseFileLocation).exists()) {
                    connection.getMetaData();
                }
            } catch (SQLException e) {
                throw new ContactEwaException("Database connection error");
            }
        }

        /*
         * Create table
         */
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS <TBL>"
                    + " ("
                    + "guildId text NOT NULL,"
                    + "entryType int NOT NULL,"
                    + "entry text NOT NULL,"
                    + "CONSTRAINT PK_<TBL> PRIMARY KEY(guildId, entryType)"
                    + ");"
                    .replaceAll("<TBL>", tableName));
        } catch (SQLException e) {
            throw new ContactEwaException("Table creation error");
        }
    }

    public static <T> T getData(String guild, DatabaseEntryType<T> type) {
        if (databaseEntryTypes == null) {
            throw new BadStateException("No DatabaseEntryTypes provided");
        }
        if (!databaseEntryTypes.contains(type)) {
            throw new BadStateException("DatabaseEntryType not recognised: " + type.getReturnClass().getName());
        }
        getConnection();

        String sql = "Select * FROM " + tableName + " WHERE guildId = ? AND entryType = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, guild);
            ps.setInt(2, type.getKey());
            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            String json = rs.getString("entry");
            if (rs.next()) {
                throw new BadStateException("Database entry not unique");
            }
            final Gson gson = new GsonBuilder().registerTypeAdapter(type.getReturnClass(), type.getDeserializer()).create();
            return gson.fromJson(json, type.getReturnClass());
        } catch (SQLException e) {
            throw new BadStateException("Database query failed");
        }
    }

    public static void deleteData() {
        // TODO
    }

    // TODO Remove
    private static <T extends com.google.gson.JsonDeserializer<T>> Object parseJson(String json, Class<JsonDeserializer<T>> clazz) {
        JsonDeserializer<T> object;
        try {
            object = clazz.getConstructor(String.class).newInstance(json);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new BadStateException("Class construction failed");
        }
        final Gson gson = new GsonBuilder().registerTypeAdapter(clazz, object).create();
        return gson.fromJson(json, clazz);
    }

    /**
     * Change the database mode so that testing doesn't affect live data
     */
    public static void setTestMode() {
        url = urlPrefix + "JuuzoTest.db";
    }


    public static boolean isInTestMode() {
        // TODO Testing
        return !url.equals(urlPrefix + databaseFileLocation);
    }


    /**
     * @throws ContactEwaException if an SQLException occurs
     */
    private static void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new ContactEwaException("Close connection error");
        }
    }
}
