package BotFrameworkBox;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public interface DatabaseEntryType<T> {
    Class<T> getReturnClass();

    JsonDeserializer<T> getDeserializer();

    JsonSerializer<T> getSerializer();
}
