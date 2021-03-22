package BotFrameworkBox;

import ExceptionsBox.BadStateException;
import ExceptionsBox.ContactEwaException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.sql.*;
import java.util.Map;

public class DatabaseWrapper {
    /**
     * Maps the key that will be used in the database to the name of a class that extends DatabaseEntryType
     * Used to prevent duplicate keys as keys are user-defined
     */
    private static final BiMap<Integer, String> databaseEntryTypes = HashBiMap.create();

    /*
     * Database connection
     */
    private static final String databaseFileLocation = "Tatsuya.db";
    // Used to establish the connection to the database
    private static final String urlPrefix = "jdbc:sqlite:" + Bot.getPathToTatsuyaBot();
    private static String url = urlPrefix + databaseFileLocation;
    private static final String tableName = "blobs";
    private static Connection connection = null;

    public static void setDatabaseEntryTypes(BiMap<Integer, Class> databaseEntryTypes) {
        for (Map.Entry<Integer, Class> entry : databaseEntryTypes.entrySet()) {
            if (!DatabaseEntryType.class.isAssignableFrom(entry.getValue())) {
                throw new BadStateException("Database entry types must extend DatabaseItem");
            }
            DatabaseWrapper.databaseEntryTypes.put(entry.getKey(), entry.getValue().getName());
        }
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
            }
            catch (SQLException e) {
                throw new ContactEwaException("Database connection error");
            }
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

    // TODO change type param to type Class if possible
    public static <T> T getData(String guild, DatabaseEntryType<T> type) {
        if (!databaseEntryTypes.containsValue(type.getClass().getName())) {
            throw new BadStateException("DatabaseEntryType not recognised: " + type.getReturnClass().getName());
        }
        getConnection();

        String returnedJson;
        String sql = "SELECT * FROM " + tableName + " WHERE guildId = ? AND entryType = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, guild);
            ps.setInt(2, databaseEntryTypes.inverse().get(type.getClass().getName()));
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
        return getGson(type, true).fromJson(returnedJson, type.getReturnClass());
    }

    public static <T> void saveData(String guild, DatabaseEntryType<T> data) {
        if (!databaseEntryTypes.containsValue(data.getClass().getName())) {
            throw new BadStateException("DatabaseEntryType not recognised: " + data.getReturnClass().getName());
        }
        getConnection();

        String json = getGson(data, false).toJson(data, data.getClass());
        String sql = "INSERT INTO " + tableName + " (guildId, entryType, entry) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, guild);
            ps.setInt(2, databaseEntryTypes.inverse().get(data.getClass().getName()));
            ps.setString(3, json);
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new BadStateException("Database query failed");
        }
    }

    private static Gson getGson(DatabaseEntryType type, boolean serialiser) {
        if (serialiser) {
            return new GsonBuilder().registerTypeAdapter(type.getReturnClass(), type.getSerializer()).create();
        }
        else {
            return new GsonBuilder().registerTypeAdapter(type.getReturnClass(), type.getDeserializer()).create();
        }
    }

    public static void deleteData() {
        // TODO
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
        try {
            if (connection != null) {
                connection.close();
            }
        }
        catch (SQLException e) {
            throw new ContactEwaException("Close connection error");
        }
    }
}
