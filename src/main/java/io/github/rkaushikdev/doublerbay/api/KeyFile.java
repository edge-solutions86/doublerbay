package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loading the key ring off a file, because the development one is not a ring.
 *
 * <p>The four keys the development ring issues are in the source of this package. They are fine for
 * a laptop and they are a published credential anywhere else, so a service that starts for real
 * reads its keys from a file the deployment owns and this is the only thing that reads it.
 *
 * <p>The file is a JSON list rather than a map. A map keyed on the key itself puts the secret in the
 * position everything logs first, and a list of objects reads the same way the issuing spreadsheet
 * does: {@code [{"key": "...", "holder": "j.mercer", "role": "chief engineer"}]}.
 *
 * <p>Nothing here writes a key file. Issuing a key is an act with a person behind it, and a service
 * that could mint its own would be a service whose audit trail starts at a program.
 */
public final class KeyFile {

    /** What one entry in the file has to carry. */
    public static final List<String> FIELDS = List.of("key", "holder", "role");

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private KeyFile() {}

    /** Read a role off the file by its written name or its short one, refusing one this service does not have. */
    public static Role roleNamed(String text) {
        for (Role role : Role.values()) {
            if (role.value().equals(text) || Text.lower(role.name()).equals(Text.lower(Text.strip(text)))) {
                return role;
            }
        }
        String every = Arrays.stream(Role.values()).map(Role::value).collect(Collectors.joining(", "));
        throw new BadReading(Literal.of(text) + " is not a role, it is one of " + every);
    }

    /** One entry off the file, said to be at a position (counting from one) when it is wrong. */
    static ApiKey entryToKey(JsonNode entry, int where) {
        if (!entry.isObject()) {
            throw new BadReading("entry " + where + " is a " + Printed.kind(entry) + ", not a key");
        }
        List<String> missing = FIELDS.stream().filter(one -> !entry.has(one)).toList();
        if (!missing.isEmpty()) {
            throw new BadReading("entry " + where + " is missing " + String.join(", ", missing));
        }
        return new ApiKey(
                Printed.of(entry.get("key")),
                Printed.of(entry.get("holder")),
                roleNamed(Printed.of(entry.get("role"))));
    }

    /** Build a ring from the text of a key file. */
    public static KeyRing ringFromText(String text) {
        JsonNode loaded;
        try {
            loaded = JSON.readTree(text);
        } catch (JsonProcessingException broken) {
            throw new BadReading("the key file is not readable json: " + broken.getOriginalMessage());
        }
        if (loaded == null || loaded.isMissingNode()) {
            throw new BadReading("the key file is not readable json: there is no value in it");
        }
        if (!loaded.isArray()) {
            throw new BadReading("a key file is a list of keys, this is a " + Printed.kind(loaded));
        }
        if (loaded.isEmpty()) {
            throw new BadReading("a key file with no keys in it would refuse every request");
        }
        KeyRing ring = new KeyRing();
        int where = 1;
        for (JsonNode entry : loaded) {
            ring.issue(entryToKey(entry, where));
            where++;
        }
        return ring;
    }

    /**
     * Read the key ring the service will accept, refusing a file that is not there or cannot be read.
     *
     * @throws UncheckedIOException when the file is there but is not UTF-8 text
     */
    public static KeyRing ringFromFile(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            throw new NotOnFile(path + " is not a key file this service can read");
        } catch (CharacterCodingException garbled) {
            throw new UncheckedIOException(garbled);
        } catch (IOException refused) {
            throw new NotOnFile(path + " could not be read: " + reason(refused));
        }
        return ringFromText(text);
    }

    /**
     * Which roles the loaded ring can actually authorise, for a start up line.
     *
     * <p>A ring with no chief on it cannot grant a deferral, and finding that out at six in the
     * morning when an aeroplane is on the ground is worse than finding it out in the start up log.
     */
    public static List<Role> rolesOn(KeyRing ring) {
        Set<Role> held = EnumSet.noneOf(Role.class);
        ring.issued().forEach(key -> held.add(key.role()));
        return List.copyOf(held);
    }

    /** The roles nothing on this ring can act as. */
    public static List<Role> missingRoles(KeyRing ring) {
        List<Role> held = rolesOn(ring);
        return Arrays.stream(Role.values()).filter(role -> !held.contains(role)).toList();
    }

    private static String reason(IOException refused) {
        if (refused instanceof AccessDeniedException) {
            return "Permission denied";
        }
        if (refused instanceof FileSystemException system && system.getReason() != null) {
            return system.getReason();
        }
        return refused.getMessage();
    }
}
