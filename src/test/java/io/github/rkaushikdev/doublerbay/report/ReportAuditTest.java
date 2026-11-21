package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.RepairPair;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportAuditTest {

    private static final Reference PAGE = Reference.parse("53-40-01");
    private static final LocalDate FITTED = LocalDate.of(2025, 4, 2);
    private static final LocalDate LATER = LocalDate.of(2026, 11, 2);
    private static final Serial MSN = new Serial("5127");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final PanelId ELSEWHERE = new PanelId(53, 10, 210, "R");

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Placement placed() {
        return Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10)));
    }

    private static RepairRecord repair(
            int sequence, PanelId part, String along, String around, boolean temporary, LocalDate on) {
        return new RepairRecord(
                new RepairId(53, sequence),
                part,
                new FindingId(2025, sequence),
                Schemes.selectScheme(inch("2.000"), inch("2.000"), placed(), PAGE, temporary),
                new Offset(inch(along), inch(around)),
                on,
                UtilisationLog.usage(13900, 24400));
    }

    private static RepairRecord repair(int sequence, PanelId part, String along, String around) {
        return repair(sequence, part, along, around, false, FITTED);
    }

    private static RepairRecord repair(int sequence, PanelId part) {
        return repair(sequence, part, "468.000", "91.000");
    }

    private static RepairRecord temporary(int sequence, PanelId part) {
        return repair(sequence, part, "468.000", "91.000", true, FITTED);
    }

    private static RepairRecord repair() {
        return repair(1, PART);
    }

    @Test
    void aTailWithNoRepairsReadsAsClean() {
        StructureAudit built = StructureAudit.audit(MSN, List.of(), FITTED);
        assertThat(built.totalRepairs()).isZero();
        assertThat(built.isClean()).isTrue();
        assertThat(built).hasToString("MSN 5127: no repairs on file");
    }

    @Test
    void oneRepairLandsOnItsPanel() {
        StructureAudit built = StructureAudit.audit(MSN, List.of(repair()), FITTED);
        assertThat(built.totalRepairs()).isEqualTo(1);
        assertThat(built.loadOn(PART)).isEqualTo(1);
        assertThat(built.loadOn(ELSEWHERE)).isZero();
    }

    @Test
    void panelsComeBackBusiestFirst() {
        List<RepairRecord> fitted = List.of(
                repair(1, PART, "468.000", "91.000"),
                repair(2, PART, "620.000", "91.000"),
                repair(3, ELSEWHERE, "700.000", "10.000"));
        List<PanelLoad> loads = StructureAudit.panelLoads(fitted);
        assertThat(loads.get(0).part()).isEqualTo(PART);
        assertThat(loads.get(0).repairs()).isEqualTo(2);
    }

    @Test
    void twoPanelsWithTheSameLoadSortByPart() {
        List<RepairRecord> fitted = List.of(repair(1, ELSEWHERE), repair(2, PART));
        assertThat(StructureAudit.panelLoads(fitted).stream()
                        .map(PanelLoad::part)
                        .toList())
                .containsExactly(PART, ELSEWHERE);
    }

    @Test
    void aPanelBelowTheThresholdIsNotCrowded() {
        StructureAudit built = StructureAudit.audit(MSN, List.of(repair(1, PART, "468.000", "91.000")), FITTED);
        assertThat(built.crowded()).isEqualTo(List.of());
    }

    @Test
    void aPanelAtTheThresholdIsCrowded() {
        List<RepairRecord> fitted = new ArrayList<>();
        for (int step = 0; step < StructureAudit.CROWDED_PANEL; step++) {
            fitted.add(repair(step + 1, PART, (468 + step * 200) + ".000", "91.000"));
        }
        StructureAudit built = StructureAudit.audit(MSN, fitted, FITTED);
        assertThat(built.crowded()).hasSize(1);
        assertThat(built.isClean()).isFalse();
    }

    @Test
    void aPanelLoadReadsBackWithItsTemporaries() {
        PanelLoad load = StructureAudit.panelLoads(List.of(temporary(1, PART))).get(0);
        assertThat(load).hasToString("5310-143L: 1 repairs, 1 temporary");
    }

    @Test
    void aPanelWithNoTemporariesSaysNothingAboutThem() {
        PanelLoad load = StructureAudit.panelLoads(List.of(repair(1, PART))).get(0);
        assertThat(load).hasToString("5310-143L: 1 repairs");
    }

    @Test
    void aPermanentRepairIsNotATemporary() {
        assertThat(StructureAudit.temporaries(List.of(repair(1, PART)), FITTED)).isEqualTo(List.of());
    }

    @Test
    void aTemporaryRepairIsListedWithItsAge() {
        List<OverdueTemporary> found = StructureAudit.temporaries(List.of(temporary(1, PART)), LATER);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).monthsFitted()).isGreaterThan(BigDecimal.valueOf(17));
    }

    @Test
    void aTemporaryRepairInsideItsLifeIsNotOverdue() {
        StructureAudit built = StructureAudit.audit(MSN, List.of(temporary(1, PART)), LocalDate.of(2025, 6, 1));
        assertThat(built.overdue()).isEqualTo(List.of());
        assertThat(built.isClean()).isTrue();
    }

    @Test
    void aTemporaryRepairPastItsLifeIsOverdue() {
        StructureAudit built = StructureAudit.audit(MSN, List.of(temporary(1, PART)), LATER);
        assertThat(built.overdue()).hasSize(1);
        assertThat(built.isClean()).isFalse();
    }

    @Test
    void theTemporaryLifeIsTheOneTheBookGivesIt() {
        assertThat(StructureAudit.TEMPORARY_MONTHS).isEqualTo(18);
    }

    @Test
    void temporariesComeBackLongestFittedFirst() {
        List<RepairRecord> fitted = List.of(
                repair(1, PART, "468.000", "91.000", true, LocalDate.of(2025, 4, 2)),
                repair(2, PART, "620.000", "91.000", true, LocalDate.of(2024, 4, 2)));
        assertThat(StructureAudit.temporaries(fitted, LATER).stream()
                        .map(one -> one.reference().sequence())
                        .toList())
                .containsExactly(2, 1);
    }

    @Test
    void anOverdueTemporaryReadsBackWithItsAge() {
        OverdueTemporary found =
                StructureAudit.temporaries(List.of(temporary(1, PART)), LATER).get(0);
        assertThat(found.toString()).startsWith("RP-53-0001 on 5310-143L,");
        assertThat(found.toString()).contains("months fitted");
    }

    @Test
    void monthsBetweenCountsToATenth() {
        assertThat(StructureAudit.monthsBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1)))
                .isEqualByComparingTo("1.0");
    }

    @Test
    void theSameDayIsNoMonths() {
        assertThat(StructureAudit.monthsBetween(FITTED, FITTED)).isEqualByComparingTo("0.0");
    }

    @Test
    void aDayBeforeTheRepairWentOnIsRefused() {
        assertThatThrownBy(() -> StructureAudit.monthsBetween(FITTED, LocalDate.of(2024, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("negative time");
    }

    @Test
    void twoRepairsTooCloseAreReportedAsAClash() {
        List<RepairRecord> fitted = List.of(repair(1, PART, "468.000", "91.000"), repair(2, PART, "469.000", "91.000"));
        StructureAudit built = StructureAudit.audit(MSN, fitted, FITTED);
        assertThat(built.clashing()).isEqualTo(List.of(new RepairPair(new RepairId(53, 1), new RepairId(53, 2))));
        assertThat(built.isClean()).isFalse();
    }

    @Test
    void twoRepairsFarApartDoNotClash() {
        List<RepairRecord> fitted = List.of(repair(1, PART, "468.000", "91.000"), repair(2, PART, "700.000", "91.000"));
        assertThat(StructureAudit.audit(MSN, fitted, FITTED).clashing()).isEqualTo(List.of());
    }

    @Test
    void theAuditLineNamesWhatItFlagged() {
        List<RepairRecord> fitted = List.of(
                repair(1, PART, "468.000", "91.000", true, LocalDate.of(2024, 1, 1)),
                repair(2, PART, "469.000", "91.000"));
        String line = StructureAudit.audit(MSN, fitted, LATER).toString();
        assertThat(line).contains("temporary repairs overdue");
        assertThat(line).contains("clashes");
    }

    @Test
    void aTailWithRepairsAndNothingWrongSaysSo() {
        StructureAudit built = StructureAudit.audit(MSN, List.of(repair(1, PART)), FITTED);
        assertThat(built.toString()).contains("nothing flagged");
    }
}
