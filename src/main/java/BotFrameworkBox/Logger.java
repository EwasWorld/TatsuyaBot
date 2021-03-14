package BotFrameworkBox;

import ExceptionsBox.BadStateException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


/*
 * Used for logging unexpected exceptions which are thrown
 * Stores the chat message which caused the exceptions along with other information from Exception
 */
public class Logger {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm z");
    private static final Path mainLogFileLocation = Paths.get(Bot.getPathToTatsuyaBot() + "LogBox/Log.txt");
    private static final Path outputLocation = Paths.get(Bot.getPathToTatsuyaBot() + "LogBox/LogReport.json");
    private static final Charset charset = StandardCharsets.UTF_8;


    /*
     * Writes the given information to a log file
     * TODO Optimisation separate Exception->String
     */
    static void logEvent(String command, Exception e) {
        try {
            final boolean isFirstLog = init(mainLogFileLocation);

            final List<String> lines = new ArrayList<>();
            if (!isFirstLog) {
                lines.add(",\n");
            }
            lines.add("{");
            lines.add(String.format("\"time\": \"%s\",", dateTimeFormatter.format(ZonedDateTime.now())));
            lines.add(String.format("\"command\": \"%s\",", command));
            lines.add(String.format("\"message\": \"%s\",", e.getMessage()));
            lines.add(String.format("\"cause\": \"%s\",", e.getCause()));
            lines.add("\"stackTrace\": \"");

            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            lines.add(sw.toString());
            pw.close();
            sw.close();

            lines.add("\"\n}");

            appendToFile(mainLogFileLocation, lines);
        } catch (IOException e1) {
            e.printStackTrace();
        }
    }


    /*
     * Creates a file if it doesn't already exists
     * Returns true if a new file was created, false if not
     */
    private static boolean init(Path path) throws IOException {
        final File file = new File(path.toString());
        if (file.exists()) {
            return false;
        }

        file.getParentFile().mkdirs();
        file.createNewFile();
        return true;
    }


    private static void appendToFile(Path outFile, List<String> lines) {
        try {
            init(outFile);
            Files.write(outFile, lines, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /*
     * Formats the log as a json file to be sent to the channel
     */
    public static File getLoggedEventsToSend() {
        try {
            if (!new File(mainLogFileLocation.toString()).exists()) {
                throw new BadStateException("No errors to report");
            }
            final List<String> lines = new ArrayList<>();
            lines.add("{\n\"log\": [\n");
            lines.addAll(Files.readAllLines(mainLogFileLocation, charset));
            lines.add("\n]\n}");

            appendToFile(outputLocation, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new File(outputLocation.toString());
    }


    public static void clearLog() {
        new File(mainLogFileLocation.toString()).delete();
        new File(outputLocation.toString()).delete();
    }
}
