package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliFleetTest {

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        return Desk.newDatabase();
    }

    private static Ran enrol(Database database) {
        return enrol(database, "5127", "N412VD");
    }

    private static Ran enrol(Database database, String serial, String marks) {
        return invoke(database, "fleet", "enrol", serial, marks, "--line-number", "1140", "--delivered", "2016-04-12");
    }

    @Test
    void aDateIsReadTheOneWay() {
        assertThat(Output.asDate("2025-03-14")).hasToString("2025-03-14");
    }

    @Test
    void columnsLineUpOnTheWidestCell() {
        String laid = Output.columns(List.of(List.of("a", "bbbb"), List.of("cccc", "d")));
        assertThat(laid.split("\n", -1)[0]).isEqualTo("a     bbbb");
        assertThat(laid.split("\n", -1)[1]).isEqualTo("cccc  d");
    }

    @Test
    void columnsOverNothingIsNothing() {
        assertThat(Output.columns(List.of())).isEmpty();
    }

    @Test
    void theSchemaCanBeBuiltFromTheCommandLine() {
        Ran answer = invoke(Databases.empty(), "init");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("built");
    }

    @Test
    void aTailCanBeEnrolledAndListed() {
        Database database = stocked();
        assertThat(enrol(database).exitCode()).isZero();
        Ran listed = invoke(database, "fleet", "list");
        assertThat(listed.output()).contains("5127");
        assertThat(listed.output()).contains("N412VD");
    }

    @Test
    void theRegisterListsNothingWhenItIsEmpty() {
        Ran listed = invoke(stocked(), "fleet", "list");
        assertThat(listed.output().strip()).isEqualTo("serial  marks  series  delivered");
    }

    @Test
    void theRegisterCanBeAskedForADay() {
        Database database = stocked();
        enrol(database);
        assertThat(invoke(database, "fleet", "list", "--on", "2020-01-01").output())
                .contains("5127");
        assertThat(invoke(database, "fleet", "list", "--on", "2000-01-01").output())
                .doesNotContain("5127");
    }

    @Test
    void aTailThatIsEnrolledTwiceIsRefused() {
        Database database = stocked();
        enrol(database);
        Ran again = enrol(database);
        assertThat(again.exitCode()).isNotZero();
        assertThat(again.failure()).contains("already on the register");
    }

    @Test
    void marksThatAreNotMarksAreRefused() {
        Database database = stocked();
        Ran answer = enrol(database, "5127", "not-a-tail");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aTailShowsItsLatestReading() {
        Database database = stocked();
        enrol(database);
        invoke(database, "fleet", "post", "5127", "2025-01-01", "--cycles", "13800", "--hours", "24200");
        Ran shown = invoke(database, "fleet", "show", "5127");
        assertThat(shown.output()).contains("13800 FC");
        assertThat(shown.output()).contains("2025-01-01");
    }

    @Test
    void aTailWithOneReadingSaysItCannotGiveARate() {
        Database database = stocked();
        enrol(database);
        assertThat(invoke(database, "fleet", "show", "5127").output()).contains("not enough readings");
    }

    @Test
    void aTailWithTwoReadingsGivesARate() {
        Database database = stocked();
        enrol(database);
        invoke(database, "fleet", "post", "5127", "2024-01-01", "--cycles", "12000", "--hours", "21000");
        invoke(database, "fleet", "post", "5127", "2025-01-01", "--cycles", "13800", "--hours", "24200");
        Ran shown = invoke(database, "fleet", "show", "5127");
        assertThat(shown.output()).contains("FC and");
    }

    @Test
    void aReadingThatRunsACounterBackwardsIsRefused() {
        Database database = stocked();
        enrol(database);
        invoke(database, "fleet", "post", "5127", "2025-01-01", "--cycles", "13800", "--hours", "24200");
        Ran answer = invoke(database, "fleet", "post", "5127", "2025-06-01", "--cycles", "100", "--hours", "200");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("cycles fell");
    }

    @Test
    void aTailThatIsNotThereIsRefused() {
        Ran answer = invoke(stocked(), "fleet", "show", "9999");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("not on the register");
    }

    @Test
    void theHelpNamesWhatTheDeskDoes() {
        Ran answer = Cli.invoke("--help");
        assertThat(answer.output()).contains("structures desk");
    }

    @Test
    void theEntryPointPrintsARefusalRatherThanThrowingIt() {
        Database database = stocked();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = terminal(out, err);
        int code = Main.run(terminal, database, "fleet", "show", "9999");
        assertThat(code).isEqualTo(Main.REFUSED);
        assertThat(err.toString()).startsWith("refused: ").contains("not on the register");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void theEntryPointThrowsOnWhatIsNotARefusal() {
        Terminal terminal = terminal(new StringWriter(), new StringWriter());
        assertThatThrownBy(() -> Main.run(
                        terminal,
                        stocked(),
                        "fleet",
                        "enrol",
                        "5127",
                        "N412VD",
                        "--series",
                        "900",
                        "--line-number",
                        "1140",
                        "--delivered",
                        "2016-04-12"))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(DoublerbayError.class);
    }

    @Test
    void aCommandLineWrittenWrongIsAUsageError() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = Main.run(terminal(out, err), stocked(), "fleet", "enrol", "5127", "N412VD");
        assertThat(code).isEqualTo(Main.MISUSED);
        assertThat(err.toString())
                .startsWith("Usage: doublerbay fleet enrol [OPTIONS] SERIAL MARKS\n"
                        + "Try 'doublerbay fleet enrol --help' for help.\n\nError: ")
                .contains("--line-number");
        assertThat(Cli.invoke("fleet", "nonsense").exitCode()).isEqualTo(Main.MISUSED);
        assertThat(Cli.invoke("fleet", "post", "5127", "2025-01-01", "--cycles", "lots", "--hours", "1")
                        .errors())
                .contains("'lots' is not a valid integer.");
    }

    @Test
    void aGroupWithNoCommandPrintsItsHelp() {
        Ran answer = Cli.invoke("fleet");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).startsWith("Usage: doublerbay fleet [OPTIONS] COMMAND [ARGS]...");
        assertThat(answer.output()).contains("enrol").contains("import").contains("--help");
        assertThat(Cli.invoke().output()).startsWith("Usage: doublerbay [OPTIONS] COMMAND [ARGS]...");
        assertThat(Cli.invoke("--database", Database.IN_MEMORY).exitCode()).isEqualTo(Main.MISUSED);
    }

    @Test
    void theDatabaseComesOffTheEnvironmentWhenTheOptionIsLeftOff() {
        Ran built = Cli.invoke(Map.of(Main.DATABASE_VARIABLE, "jdbc:h2:mem:fleet-from-the-environment"), "init");
        assertThat(built.exitCode()).isZero();
        Ran overridden = Cli.invoke(
                Map.of(Main.DATABASE_VARIABLE, "jdbc:nothing:here"), "--database", Database.IN_MEMORY, "init");
        assertThat(overridden.exitCode()).isZero();
        assertThat(overridden.output()).startsWith("built ");
    }

    private static Terminal terminal(StringWriter out, StringWriter err) {
        return new Terminal(
                Map.of(), new BufferedReader(new StringReader("")), new PrintWriter(out), new PrintWriter(err));
    }
}
