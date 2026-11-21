package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.util.JsonText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyfileTest {

    private static final List<Map<String, String>> FOUR = List.of(
            entry("reader-key-9001", "the front desk", "reader"),
            entry("mechanic-key-9002", "s.brandt", "mechanic"),
            entry("engineer-key-9003", "a.okonkwo", "engineer"),
            entry("chief-key-9004", "j.mercer", "chief engineer"));

    private static Map<String, String> entry(String key, String holder, String role) {
        Map<String, String> held = new LinkedHashMap<>();
        held.put("key", key);
        held.put("holder", holder);
        held.put("role", role);
        return held;
    }

    private static String json(Object value) {
        return JsonText.value(value);
    }

    private static Path written(Path directory) throws IOException {
        Path path = directory.resolve("keys.json");
        Files.writeString(path, json(FOUR), StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void aRoleIsReadOffItsWrittenName() {
        assertThat(KeyFile.roleNamed("chief engineer")).isSameAs(Role.CHIEF);
        assertThat(KeyFile.roleNamed("reader")).isSameAs(Role.READER);
    }

    @Test
    void aRoleIsAlsoReadOffItsShortName() {
        assertThat(KeyFile.roleNamed("CHIEF")).isSameAs(Role.CHIEF);
        assertThat(KeyFile.roleNamed(" mechanic ")).isSameAs(Role.MECHANIC);
    }

    @Test
    void aRoleThatIsNotOneNamesTheOnesThatAre() {
        assertThatThrownBy(() -> KeyFile.roleNamed("wizard"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("chief engineer");
    }

    @Test
    void aFileOfFourKeysLoads() {
        KeyRing ring = KeyFile.ringFromText(json(FOUR));
        assertThat(ring.size()).isEqualTo(4);
    }

    @Test
    void aLoadedKeyAuthorisesWhatItsRoleAllows() {
        KeyRing ring = KeyFile.ringFromText(json(FOUR));
        ApiKey holder = Security.check(ring, "chief-key-9004", Action.DEFER);
        assertThat(holder.holder()).isEqualTo("j.mercer");
    }

    @Test
    void aLoadedKeyRefusesWhatItsRoleDoesNotAllow() {
        KeyRing ring = KeyFile.ringFromText(json(FOUR));
        assertThatThrownBy(() -> Security.check(ring, "mechanic-key-9002", Action.DEFER))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("may not grant a deferral");
    }

    @Test
    void aFileThatIsNotJsonIsRefused() {
        assertThatThrownBy(() -> KeyFile.ringFromText("{not json"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not readable json");
    }

    @Test
    void aFileThatIsNotAListIsRefused() {
        assertThatThrownBy(() -> KeyFile.ringFromText(json(Map.of("key", "x"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a list of keys");
    }

    @Test
    void aFileWithNoKeysIsRefused() {
        assertThatThrownBy(() -> KeyFile.ringFromText("[]"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("would refuse every request");
    }

    @Test
    void anEntryThatIsNotAnObjectNamesItsPosition() {
        assertThatThrownBy(() -> KeyFile.ringFromText(json(List.of(FOUR.get(0), "chief-key-9004"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("entry 2 is a str");
    }

    @Test
    void anEntryMissingAFieldNamesTheField() {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("key", "chief-key-9004");
        entry.put("role", "chief engineer");
        assertThatThrownBy(() -> KeyFile.ringFromText(json(List.of(entry))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("entry 1 is missing holder");
    }

    @Test
    void anEntryMissingTwoFieldsNamesBoth() {
        assertThatThrownBy(() -> KeyFile.ringFromText(json(List.of(Map.of("key", "chief-key-9004")))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("missing holder, role");
    }

    @Test
    void aKeyThatIsTooShortIsRefused() {
        assertThatThrownBy(() -> KeyFile.ringFromText(json(List.of(entry("abc", "j.mercer", "chief engineer")))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least eight characters");
    }

    @Test
    void aKeyWithNoHolderIsRefused() {
        assertThatThrownBy(() -> KeyFile.ringFromText(json(List.of(entry("chief-key-9004", "   ", "chief engineer")))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("belongs to somebody");
    }

    @Test
    void theSameKeyTwiceIsRefused() {
        assertThatThrownBy(() -> KeyFile.ringFromText(json(List.of(FOUR.get(0), FOUR.get(0)))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already been issued");
    }

    @Test
    void aFileIsReadOffDisk(@TempDir Path directory) throws IOException {
        assertThat(KeyFile.ringFromFile(written(directory)).size()).isEqualTo(4);
    }

    @Test
    void aFileThatIsNotThereIsRefused(@TempDir Path directory) {
        assertThatThrownBy(() -> KeyFile.ringFromFile(directory.resolve("missing.json")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not a key file");
    }

    @Test
    void aDirectoryIsNotAKeyFile(@TempDir Path directory) {
        assertThatThrownBy(() -> KeyFile.ringFromFile(directory)).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aFullRingIsMissingNoRoles(@TempDir Path directory) throws IOException {
        KeyRing ring = KeyFile.ringFromFile(written(directory));
        assertThat(KeyFile.missingRoles(ring)).isEmpty();
        assertThat(KeyFile.rolesOn(ring)).hasSize(4);
    }

    @Test
    void aRingWithNoChiefSaysWhichRoleIsMissing() {
        KeyRing ring = KeyFile.ringFromText(json(FOUR.subList(0, 3)));
        assertThat(KeyFile.missingRoles(ring)).containsExactly(Role.CHIEF);
    }

    @Test
    void aRingWithOnlyReadersIsMissingThreeRoles() {
        KeyRing ring = KeyFile.ringFromText(json(FOUR.subList(0, 1)));
        assertThat(KeyFile.missingRoles(ring)).hasSize(3);
    }

    @Test
    void anEmptyRingHasNoRolesOnIt() {
        assertThat(KeyFile.rolesOn(new KeyRing())).isEmpty();
    }
}
