package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliOrderingTest {

    private static final String DAY = "2025-03-14";
    private static final String SENT = "2025-03-20";
    private static final String ANSWERED = "2025-06-01";
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";
    private static final FindingId FIRST = new FindingId(2025, 1);
    private static final OrderId ORDER = new OrderId(2025, 1);

    @TempDir
    Path tmp;

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        return Desk.stocked();
    }

    private static String assessed(Database database) {
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(1));
            unit.dispositions()
                    .save(DispositionRecord.raised(FIRST)
                            .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "a.okonkwo"));
        });
        return FIRST.toString();
    }

    private String questionFile() {
        return Cli.writtenText(tmp, "question.txt", QUESTION).toString();
    }

    private Ran raiseOne(Database database, String reference) {
        return invoke(
                database, "order", "raise", reference, "--question", questionFile(), "--on", DAY, "--by", "a.okonkwo");
    }

    private static Ran approved(Database database, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "order",
                "approved",
                "EO-2025-0001",
                "--sheet",
                "RAS-2025-4417",
                "--category",
                "B",
                "--on",
                ANSWERED,
                "--by",
                "a.okonkwo"));
        args.addAll(List.of(extra));
        return invoke(database, args.toArray(String[]::new));
    }

    private static DispositionState stateOf(Database database) {
        return UnitOfWork.apply(database, unit -> unit.dispositions().get(FIRST).state());
    }

    @Test
    void aCategoryIsReadOffItsLetter() {
        assertThat(Ordering.categoryNamed("b")).isSameAs(RepairCategory.B);
        assertThat(Ordering.categoryNamed(" A ")).isSameAs(RepairCategory.A);
    }

    @Test
    void aCategoryThatIsNotOneNamesTheOnesThatAre() {
        assertThatThrownBy(() -> Ordering.categoryNamed("D"))
                .isInstanceOf(Misused.class)
                .hasMessageContaining("A, B, C");
    }

    @Test
    void anEmptyQuestionFileIsRefused() {
        Path path = Cli.writtenText(tmp, "question.txt", "   \n");
        assertThatThrownBy(() -> Ordering.readQuestion(path))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no question in it");
    }

    @Test
    void noConditionsFileMeansNoConditions() {
        assertThat(Ordering.conditionsFrom(null)).isEmpty();
    }

    @Test
    void conditionsComeOffTheFileOneALine() {
        Path path =
                Cli.writtenText(tmp, "conditions.txt", "inspect at every C check\n\ndo not extend past the heavy\n");
        assertThat(Ordering.conditionsFrom(path))
                .containsExactly("inspect at every C check", "do not extend past the heavy");
    }

    @Test
    void anOrderCanBeRaised() {
        Database database = stocked();
        Ran answer = raiseOne(database, assessed(database));
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("EO-2025-0001 raised");
    }

    @Test
    void raisingHoldsTheCard() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        assertThat(stateOf(database)).isSameAs(DispositionState.HELD);
    }

    @Test
    void theQuestionOffTheFileIsWhatGetsStored() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        String stored =
                UnitOfWork.apply(database, unit -> unit.orders().get(ORDER).question());
        assertThat(stored).isEqualTo(QUESTION);
    }

    @Test
    void aQuestionFileThatIsNotThereIsRefused() {
        Database database = stocked();
        Ran answer = invoke(
                database,
                "order",
                "raise",
                assessed(database),
                "--question",
                tmp.resolve("missing.txt").toString(),
                "--on",
                DAY,
                "--by",
                "a.okonkwo");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void anOrderCanBeMarkedSent() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        Ran answer = invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        assertThat(answer.output()).contains("EO-2025-0001 sent");
    }

    @Test
    void anOrderCanBeAcknowledged() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        assertThat(invoke(database, "order", "acknowledged", "EO-2025-0001").output())
                .contains("acknowledged");
    }

    @Test
    void anApprovalIsRecorded() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        Ran answer = approved(database, "--valid-until", "2027-01-01");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("approved on RAS-2025-4417");
    }

    @Test
    void anApprovalReleasesTheCard() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        approved(database);
        assertThat(stateOf(database)).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void theConditionsFileReachesTheStoredApproval() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        Path conditions = Cli.writtenText(tmp, "conditions.txt", "inspect at every C check\n");
        approved(database, "--conditions", conditions.toString());
        RepairApproval held = UnitOfWork.apply(
                database, unit -> unit.orders().get(ORDER).approval().orElseThrow());
        assertThat(held.conditions()).containsExactly("inspect at every C check");
    }

    @Test
    void aRejectionIsRecorded() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        Ran answer = invoke(database, "order", "rejected", "EO-2025-0001", "--on", ANSWERED, "--by", "a.okonkwo");
        assertThat(answer.output()).contains("rejected");
    }

    @Test
    void aQuestionCanBeWithdrawn() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        Ran answer = invoke(database, "order", "withdraw", "EO-2025-0001", "--on", ANSWERED, "--by", "a.okonkwo");
        assertThat(answer.output()).contains("withdrawn");
    }

    @Test
    void nothingOutSaysSo() {
        Database database = stocked();
        assertThat(invoke(database, "order", "list").output().strip()).isEqualTo("nothing out to the manufacturer");
    }

    @Test
    void theOpenListCarriesWhatIsOut() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        String listed = invoke(database, "order", "list").output();
        assertThat(listed).contains("EO-2025-0001");
        assertThat(listed).contains("DR-2025-000001");
    }

    @Test
    void nothingNeedsChasingTheDayItGoesOut() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        assertThat(invoke(database, "order", "chase", "--on", SENT).output()).contains("nothing waiting");
    }

    @Test
    void anOrderOutALongTimeNeedsChasing() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        String printed =
                invoke(database, "order", "chase", "--on", "2026-01-01").output();
        assertThat(printed).contains("EO-2025-0001");
        assertThat(printed).contains("days waiting");
    }

    @Test
    void oneOrderCanBeShown() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        String printed = invoke(database, "order", "show", "EO-2025-0001").output();
        assertThat(printed).contains(QUESTION);
        assertThat(printed).contains("not yet");
    }

    @Test
    void aShownApprovalListsItsConditions() {
        Database database = stocked();
        raiseOne(database, assessed(database));
        invoke(database, "order", "sent", "EO-2025-0001", "--on", SENT);
        Path conditions = Cli.writtenText(tmp, "conditions.txt", "inspect at every C check\n");
        approved(database, "--conditions", conditions.toString());
        String printed = invoke(database, "order", "show", "EO-2025-0001").output();
        assertThat(printed).contains("  condition");
        assertThat(printed).contains("no end");
    }

    @Test
    void anOrderThatIsNotOnFileCannotBeShown() {
        Database database = stocked();
        assertThat(invoke(database, "order", "show", "EO-2025-0099").exitCode()).isNotZero();
    }
}
