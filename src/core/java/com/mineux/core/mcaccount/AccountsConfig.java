package com.mineux.core.mcaccount;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The saved accounts, read and written the way every other config is: one
 * pretty-printed json file under config/mineux, best-effort, defaults on a bad read.
 * <p>
 * It does not hand Gson the account objects directly, because {@link Account} is abstract and
 * a cracked row and a Microsoft one are not the same shape -- letting Gson guess the subtype
 * off the json is the kind of reflection-driven cleverness that breaks the first time the
 * file is edited by hand. Instead every account flattens to a {@link Stored} record on the
 * way out and is rebuilt from its {@code type} tag on the way in, so the on-disk form is flat
 * data with a discriminator and the runtime form is real objects. An unknown or malformed row
 * is dropped rather than failing the whole load: one corrupt entry should cost you that
 * account, not every account.
 * <p>
 * The in-memory list is the source of truth once loaded; {@link #add} and {@link #remove}
 * mutate it and write straight through, so the file always matches what the switcher shows.
 */
public final class AccountsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/accounts");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AccountsConfig instance;

    private final List<Account> accounts = new ArrayList<>();

    /** The flat, Gson-friendly form of one account. Credential fields are populated per type. */
    public static final class Stored {
        public String type;
        public String name;
        public String username;
        public String uuid;
        public String refreshToken;
        /** A {@link SessionAccount}'s Minecraft access token. */
        public String accessToken;
    }

    /** The file's top-level shape: just the list, so the json reads as an object not a bare array. */
    private static final class Data {
        List<Stored> accounts = new ArrayList<>();
    }

    private AccountsConfig() {}

    public static synchronized AccountsConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** A read-only view for the switcher to iterate; mutate through {@link #add}/{@link #remove}. */
    public synchronized List<Account> accounts() {
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }

    public synchronized int size() {
        return accounts.size();
    }

    public synchronized void add(Account account) {
        accounts.add(account);
        save();
    }

    /**
     * Adds an account, or replaces the saved one that is the same player -- signing in to a
     * Microsoft account a second time refreshes its row rather than listing it twice.
     */
    public synchronized void put(Account account) {
        for (int i = 0; i < accounts.size(); i++) {
            Account existing = accounts.get(i);
            boolean same = !account.uuid().isEmpty() && account.uuid().equalsIgnoreCase(existing.uuid())
                && account.type().equals(existing.type());
            if (same) {
                accounts.set(i, account);
                save();
                return;
            }
        }
        add(account);
    }

    public synchronized void remove(Account account) {
        accounts.remove(account);
        save();
    }

    // ---- disk --------------------------------------------------------

    private static Path file() {
        return ConfigFile.directory().resolve("accounts.json");
    }

    private static AccountsConfig load() {
        AccountsConfig config = new AccountsConfig();
        Path file = file();

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Data data = GSON.fromJson(reader, Data.class);

                if (data != null && data.accounts != null) {
                    for (Stored stored : data.accounts) {
                        Account account = fromStored(stored);
                        if (account != null) config.accounts.add(account);
                    }
                }
                return config;
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read {}, starting with no saved accounts", file, e);
            }
        }

        return config;
    }

    /** Best-effort write. A failure costs the last change, never the session -- see SpoofConfig. */
    private synchronized void save() {
        Path file = file();

        Data data = new Data();
        for (Account account : accounts) data.accounts.add(account.toStored());

        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    /** Rebuilds one account from its stored row, or null if the row is unusable. */
    private static Account fromStored(Stored stored) {
        if (stored == null || stored.type == null) return null;

        // An if chain, not a switch on the type: see Words for why this code has none.
        Account account;
        if (Objects.equals(Account.CRACKED, stored.type)) {
            account = stored.name == null ? null : new CrackedAccount(stored.name);
        } else if (Objects.equals(Account.MICROSOFT, stored.type)) {
            account = stored.refreshToken == null ? null
                : new MicrosoftAccount(stored.refreshToken, orEmpty(stored.name));
        } else if (Objects.equals(Account.SESSION, stored.type)) {
            account = stored.accessToken == null ? null
                : new SessionAccount(stored.accessToken, orEmpty(stored.name));
        } else {
            LOGGER.warn("Dropping saved account with unknown type '{}'", stored.type);
            account = null;
        }

        if (account != null) {
            // Restore the display cache so the row has a name before its first login this run.
            account.username = orEmpty(stored.username);
            account.uuid = orEmpty(stored.uuid);
        }
        return account;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
