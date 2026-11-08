package com.mineux.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mineux.core.MineUX;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * One json file under the config dir (config/mineux), read and written the way every module's config
 * wants it read and written.
 * <p>
 * The rules are the same for all of them and none of them are obvious, which is why they
 * live here rather than three times over: a file that cannot be read is a warning and a set
 * of defaults rather than a crash, because a config the player cannot open the game to fix
 * is worse than a config that lost its settings; a file that parses to nothing counts as
 * absent, because that is what an empty or truncated file gives you; and every load ends in
 * {@link Backed#ensureDefaults()}, so the rest of the client never has to ask whether the
 * thing it is reading came off disk or out of a constructor.
 * <p>
 * Writing is best-effort for the same reason -- a full disk should cost you the setting you
 * just changed, not the session.
 *
 * @param <T> the config this file holds
 */
public final class ConfigFile<T extends ConfigFile.Backed> {

    /** Pretty-printed because these files are meant to be opened and edited by hand. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Class<T> type;
    private final String name;
    private final Supplier<T> fresh;
    private final Logger log;

    /**
     * @param type  the class GSON deserialises into
     * @param name  the file name inside the client's config directory
     * @param fresh a config with nothing loaded into it, for a first run or a bad read
     * @param log   the owning module's logger, so a warning names the module and not this
     */
    public ConfigFile(Class<T> type, String name, Supplier<T> fresh, Logger log) {
        this.type = type;
        this.name = name;
        this.fresh = fresh;
        this.log = log;
    }

    /**
     * Where every one of these files lives: the host's config dir ({@code config/mineux} for
     * MineUX itself). Before the host is up -- the unit tests -- a folder under build/.
     */
    public static Path directory() {
        if (MineUX.ready()) return MineUX.host().configDir();
        return Path.of("build", "test-config", "mineux");
    }

    public Path path() {
        return directory().resolve(name);
    }

    /**
     * The config as it stands on disk, repaired, or a fresh one written out so that the
     * next launch has something to read and the player has something to edit.
     */
    public T load() {
        Path file = path();

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                T loaded = GSON.fromJson(reader, type);

                if (loaded != null) {
                    loaded.ensureDefaults();
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not read {}, falling back to defaults", file, e);
            }
        }

        T value = fresh.get();
        value.ensureDefaults();
        save(value);
        return value;
    }

    public void save(T value) {
        Path file = path();

        try {
            Files.createDirectories(file.getParent());

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            log.warn("Could not write {}", file, e);
        }
    }

    /**
     * A config that can put itself right after a read.
     * <p>
     * Every one of these files is editable by hand and older ones are missing keys the
     * client has since grown, so what comes back from GSON is a suggestion rather than a
     * state. The implementation is where a module says what it will accept.
     */
    public interface Backed {
        void ensureDefaults();
    }
}
