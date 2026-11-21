package io.github.rkaushikdev.doublerbay.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static final Map<String, String> MINIMUM = Map.of(
            "DOUBLERBAY_DATABASE", "jdbc:h2:file:./desk",
            "DOUBLERBAY_KEYS", "/etc/doublerbay/keys.json");

    private static Map<String, String> env(String... extra) {
        Map<String, String> held = new HashMap<>(MINIMUM);
        for (int index = 0; index < extra.length; index += 2) {
            held.put(extra[index], extra[index + 1]);
        }
        return held;
    }

    @Test
    void aSettingIsReadOffAPrefixedVariable() {
        assertThat(Config.named("database")).isEqualTo("DOUBLERBAY_DATABASE");
        assertThat(Config.named("recent_days")).isEqualTo("DOUBLERBAY_RECENT_DAYS");
    }

    @Test
    void aSettingThatIsNotThereReadsAsNothing() {
        assertThat(Config.read("host", Map.of())).isEmpty();
    }

    @Test
    void aBlankSettingReadsAsNothing() {
        assertThat(Config.read("host", Map.of("DOUBLERBAY_HOST", "   "))).isEmpty();
    }

    @Test
    void aSettingIsTrimmed() {
        assertThat(Config.read("host", Map.of("DOUBLERBAY_HOST", "  0.0.0.0  ")))
                .contains("0.0.0.0");
    }

    @Test
    void aRequiredSettingThatIsMissingIsRefusedByName() {
        assertThatThrownBy(() -> Config.required("database", Map.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("DOUBLERBAY_DATABASE is not set");
    }

    @Test
    void aRequiredSettingThatIsBlankIsRefused() {
        assertThatThrownBy(() -> Config.required("database", Map.of("DOUBLERBAY_DATABASE", "")))
                .isInstanceOf(BadReading.class);
    }

    @Test
    void aRequiredSettingThatIsThereComesBack() {
        assertThat(Config.required("database", MINIMUM)).isEqualTo("jdbc:h2:file:./desk");
    }

    @Test
    void aCountFallsBackWhenItIsNotSet() {
        assertThat(Config.wholeNumber("port", 8000, Map.of())).isEqualTo(8000);
    }

    @Test
    void aCountIsReadWhenItIsSet() {
        assertThat(Config.wholeNumber("port", 8000, Map.of("DOUBLERBAY_PORT", "9001")))
                .isEqualTo(9001);
    }

    @Test
    void aCountThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> Config.wholeNumber("port", 8000, Map.of("DOUBLERBAY_PORT", "soon")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a whole number");
    }

    @Test
    void aDayThatIsNotSetReadsAsNothing() {
        assertThat(Config.aDay("today", Map.of())).isEmpty();
    }

    @Test
    void aDayThatIsSetIsRead() {
        assertThat(Config.aDay("today", Map.of("DOUBLERBAY_TODAY", "2025-03-14")))
                .contains(LocalDate.of(2025, 3, 14));
    }

    @Test
    void aDayThatIsNotADayIsRefused() {
        assertThatThrownBy(() -> Config.aDay("today", Map.of("DOUBLERBAY_TODAY", "14/03/2025")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a date");
    }

    @Test
    void theMinimumEnvironmentBuildsSettings() {
        Settings settings = Config.fromEnvironment(env());
        assertThat(settings.database()).isEqualTo("jdbc:h2:file:./desk");
        assertThat(settings.keys()).isEqualTo(Path.of("/etc/doublerbay/keys.json"));
    }

    @Test
    void theDefaultsAreUsedWhenNothingElseIsSet() {
        Settings settings = Config.fromEnvironment(env());
        assertThat(settings.host()).isEqualTo(Config.DEFAULT_HOST);
        assertThat(settings.port()).isEqualTo(Config.DEFAULT_PORT);
        assertThat(settings.recentDays()).isEqualTo(Config.DEFAULT_RECENT_DAYS);
        assertThat(settings.hasAPinnedDay()).isFalse();
    }

    @Test
    void aDatabaseThatIsNotSetStopsTheServiceStarting() {
        assertThatThrownBy(() -> Config.fromEnvironment(Map.of("DOUBLERBAY_KEYS", "/etc/keys.json")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("DOUBLERBAY_DATABASE");
    }

    @Test
    void aKeyFileThatIsNotSetStopsTheServiceStarting() {
        assertThatThrownBy(() -> Config.fromEnvironment(Map.of("DOUBLERBAY_DATABASE", "jdbc:h2:file:./desk")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("DOUBLERBAY_KEYS");
    }

    @Test
    void theHostCanBeOverridden() {
        assertThat(Config.fromEnvironment(env("DOUBLERBAY_HOST", "0.0.0.0")).host())
                .isEqualTo("0.0.0.0");
    }

    @Test
    void thePortCanBeOverridden() {
        assertThat(Config.fromEnvironment(env("DOUBLERBAY_PORT", "9001")).port())
                .isEqualTo(9001);
    }

    @Test
    void aPinnedDayReachesTheSettings() {
        Settings settings = Config.fromEnvironment(env("DOUBLERBAY_TODAY", "2025-03-14"));
        assertThat(settings.hasAPinnedDay()).isTrue();
        assertThat(settings.today()).contains(LocalDate.of(2025, 3, 14));
    }

    @Test
    void aPortBelowTheRangeIsRefused() {
        assertThatThrownBy(() -> Config.fromEnvironment(env("DOUBLERBAY_PORT", "0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a port");
    }

    @Test
    void aPortAboveTheRangeIsRefused() {
        assertThatThrownBy(() -> Config.fromEnvironment(env("DOUBLERBAY_PORT", "70000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a port");
    }

    @Test
    void aRecentWindowOfNothingIsRefused() {
        assertThatThrownBy(() -> Config.fromEnvironment(env("DOUBLERBAY_RECENT_DAYS", "0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least a day");
    }

    @Test
    void anInMemoryDatabaseSaysSo() {
        assertThat(Config.fromEnvironment(env("DOUBLERBAY_DATABASE", "jdbc:h2:mem:desk"))
                        .isInMemory())
                .isTrue();
        assertThat(Config.fromEnvironment(env("DOUBLERBAY_DATABASE", ":memory:"))
                        .isInMemory())
                .isTrue();
    }

    @Test
    void aDatabaseOnDiskSaysSo() {
        assertThat(Config.fromEnvironment(env()).isInMemory()).isFalse();
    }

    @Test
    void theSettingsReadBackAsOneLine() {
        Settings settings = Config.fromEnvironment(env());
        assertThat(settings).hasToString("doublerbay on 127.0.0.1:8000, database on disk");
    }

    @Test
    void everyVariableTheServiceReadsCanBeListed() {
        List<String> every = Config.described();
        assertThat(every).contains("DOUBLERBAY_DATABASE", "DOUBLERBAY_TODAY");
        assertThat(every).hasSize(6);
    }

    @Test
    void settingsCanBeBuiltDirectly() {
        Settings settings = new Settings("jdbc:h2:file:./desk", Path.of("keys.json"));
        assertThat(settings.port()).isEqualTo(Config.DEFAULT_PORT);
    }
}
