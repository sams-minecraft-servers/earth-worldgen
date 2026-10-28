package tj.earth.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * The mod's settings files, in config/earth-mod/ in the server's folder. Each is written with every
 * setting and its default the first time it is read; settings added by a newer version are added
 * (with their defaults) the next time; nothing already in a file is changed.
 */
final class ConfigFile {
    private ConfigFile() {}

    static final Path FOLDER = Path.of("config", "earth-mod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Reads config/earth-mod/<name>, filling in and writing back what is missing. {@code older} is where an earlier version kept it, or null. */
    static <T> T read(final String name, final Class<T> type, final T defaults, final Path older) {
        Path file = FOLDER.resolve(name);
        try {
            if (!Files.exists(file) && older != null && Files.exists(older)) {
                Files.createDirectories(FOLDER);
                Files.move(older, file);
                EarthWorldgen.LOG.info("Earth: moved {} to {}", older, file);
            }
            JsonObject json = new JsonObject();
            if (Files.exists(file)) {
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement read = JsonParser.parseReader(in);
                    if (read.isJsonObject()) json = read.getAsJsonObject();
                }
            }
            if (addMissing(json, GSON.toJsonTree(defaults).getAsJsonObject()) || !Files.exists(file)) {
                try {
                    Files.createDirectories(FOLDER);
                    Path part = file.resolveSibling(name + ".part");
                    Files.writeString(part, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
                    Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    EarthWorldgen.LOG.warn("Earth: could not write {} ({}); using the settings as read", file, e.getMessage());
                }
            }
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(file + " could not be read: " + e.getMessage(), e);
        }
    }

    /** Adds every default the file does not have yet, at any depth. True if anything was added. */
    private static boolean addMissing(final JsonObject json, final JsonObject defaults) {
        boolean added = false;
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            JsonElement have = json.get(entry.getKey());
            if (have == null) {
                json.add(entry.getKey(), entry.getValue().deepCopy());
                added = true;
            } else if (have.isJsonObject() && entry.getValue().isJsonObject()) {
                added |= addMissing(have.getAsJsonObject(), entry.getValue().getAsJsonObject());
            }
        }
        return added;
    }
}
