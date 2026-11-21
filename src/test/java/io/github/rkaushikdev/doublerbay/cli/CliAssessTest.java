package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliAssessTest {

    private static final String DAY = "2025-03-14";

    @TempDir
    Path tmp;

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        Database database = Desk.newDatabase();
        invoke(database, "fleet", "enrol", "5127", "N412VD", "--line-number", "1140", "--delivered", "2016-04-12");
        invoke(database, "fleet", "post", "5127", DAY, "--cycles", "13800", "--hours", "24200");
        return database;
    }

    private static Map<String, Object> dent(String depth) {
        return dent(depth, 15, 6);
    }

    private static Map<String, Object> dent(String depth, int frame, int stringer) {
        Map<String, Object> card = Cards.templateFor(DamageKind.DENT, "fuselage");
        card.put("serial", "5127");
        card.put("found_on", DAY);
        card.put("origin", "impact");
        card.put("source", "walkaround");
        card.put("at_usage", Map.of("cycles", 13800, "hours", "24200.0"));
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("structure", "fuselage");
        where.put("frame", frame);
        where.put("aft_of_frame", "10.000");
        where.put("stringer", stringer);
        where.put("below_stringer", "0.000");
        where.put("side", "L");
        card.put("fuselage", where);
        Map<String, Object> measured = new LinkedHashMap<>();
        measured.put("depth", depth);
        measured.put("along", "1.500");
        measured.put("around", "1.200");
        measured.put("sharp_bottom", false);
        measured.put("cracked", false);
        card.put("measurements", measured);
        return card;
    }

    private String raiseCard(Database database, Map<String, Object> card) {
        return raiseCard(database, card, "card.json");
    }

    private String raiseCard(Database database, Map<String, Object> card, String name) {
        Path path = Cli.written(tmp, name, card);
        Ran answer = invoke(database, "cards", "raise", path.toString());
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        return answer.lastWord();
    }

    @Test
    void aShallowDentComesBackAllowable() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.010"));
        Ran answer = invoke(database, "assess", reference);
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("allowable");
    }

    @Test
    void theReasonsArePrintedUnderTheDisposition() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.010"));
        String printed = invoke(database, "assess", reference).output();
        assertThat(printed).contains("  ok ");
        assertThat(printed).contains("disposition");
    }

    @Test
    void aDeepDentFailsANamedRule() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.400"));
        String printed = invoke(database, "assess", reference).output();
        assertThat(printed).contains("  no ");
        assertThat(printed).doesNotContain("allowable as it stands");
    }

    @Test
    void thePageItCameOffIsPrinted() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.010"));
        assertThat(invoke(database, "assess", reference).output()).contains("rev ");
    }

    @Test
    void aDayCanBeAskedFor() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.010"));
        assertThat(invoke(database, "assess", reference, "--on", "2025-06-01").exitCode())
                .isZero();
    }

    @Test
    void aDayThatIsNotADayIsRefused() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.010"));
        Ran answer = invoke(database, "assess", reference, "--on", "01-06-2025");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("yyyy-mm-dd");
    }

    @Test
    void aCardThatIsNotThereCannotBeAssessed() {
        Ran answer = invoke(stocked(), "assess", "DR-2025-0099");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aTailWithNothingOpenSaysSo() {
        Ran answer = invoke(stocked(), "assess-tail", "5127", "--on", DAY);
        assertThat(answer.output().strip()).isEqualTo("nothing open on 5127");
    }

    @Test
    void aTailAssessmentListsItsPanels() {
        Database database = stocked();
        raiseCard(database, dent("0.010"));
        raiseCard(database, dent("0.010", 40, 12), "second.json");
        String printed = invoke(database, "assess-tail", "5127", "--on", DAY).output();
        assertThat(printed).contains("whole tail");
        assertThat(printed.strip().split("\n", -1).length).isGreaterThanOrEqualTo(4);
    }

    @Test
    void theWholeTailLineCarriesTheWorstAnswer() {
        Database database = stocked();
        raiseCard(database, dent("0.010"));
        raiseCard(database, dent("0.400", 40, 12), "second.json");
        String[] lines = invoke(database, "assess-tail", "5127", "--on", DAY)
                .output()
                .strip()
                .split("\n", -1);
        String last = lines[lines.length - 1];
        assertThat(last).doesNotContain("allowable as it stands");
    }

    @Test
    void verdictLinesStartWithTheCardAndEndWithReasons() {
        Database database = stocked();
        String reference = raiseCard(database, dent("0.010"));
        List<List<String>> lines = UnitOfWork.apply(database, unit -> {
            Finding card = unit.findings().latest(FindingId.parse(reference));
            TailContext tail = StoreContext.contextFor(unit, new TypeCatalogue(), new Serial("5127"), card.foundOn());
            return Assessing.verdictLines(Assessor.assess(Book.v320(), tail, card));
        });
        assertThat(lines.get(0).get(0)).isEqualTo("card");
        assertThat(lines.get(lines.size() - 1).get(0)).startsWith("  ");
    }

    @Test
    void aTailThatIsNotOnTheRegisterCannotBeAssessed() {
        Ran answer = invoke(stocked(), "assess-tail", "9999", "--on", DAY);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aTailAssessedWithAnEmptyDayIsAUsageError() {
        Ran answer = invoke(stocked(), "assess-tail", "5127", "--on", "");
        assertThat(answer.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(answer.errors()).contains("Error: assessing a tail needs a day");
    }
}
