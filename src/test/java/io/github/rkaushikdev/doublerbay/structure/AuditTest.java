package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.complaints.Complaint;
import io.github.rkaushikdev.doublerbay.complaints.Severity;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.v320.Fuselage;
import io.github.rkaushikdev.doublerbay.v320.Wing;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static List<Complaint> saying(List<Complaint> complaints, String words) {
        return complaints.stream()
                .filter(complaint -> complaint.says().contains(words))
                .toList();
    }

    @Test
    void theGridBaysAreEverySquareTheLaddersBound() {
        FuselageGrid built = Sample.grid();
        List<Bay> left = Audits.gridBays(built, Side.LEFT);
        assertThat(left)
                .hasSize((built.frames().size() - 1) * (built.stringers().size() - 1));
        assertThat(left).allMatch(Bay::isSingle);
        assertThat(left).allMatch(bay -> bay.side() == Side.LEFT);
    }

    @Test
    void theTranscribedBarrelHasNothingToComplainAbout() {
        assertThat(Audits.auditAirframe(Fuselage.barrel())).isEmpty();
    }

    @Test
    void theTranscribedWingHasNothingToComplainAbout() {
        assertThat(Audits.auditWingBox(Wing.wingBox())).isEmpty();
    }

    @Test
    void aBayTwoPanelsClaimIsBlocking() {
        Airframe doubled = new Airframe(
                "test",
                Sample.grid(),
                List.of(Sample.forwardUpper(), Sample.aftUpper(), Sample.forwardLower(), Sample.overlapping()));
        List<Complaint> claimed = saying(Audits.auditAirframe(doubled), "panels claim this bay");
        assertThat(claimed).isNotEmpty();
        assertThat(claimed).allMatch(complaint -> complaint.severity() == Severity.BLOCKING);
    }

    @Test
    void aBayNoPanelCoversIsAdvisory() {
        List<Complaint> uncovered = Audits.auditAirframe(Sample.airframe()).stream()
                .filter(complaint -> complaint.says().equals("no panel covers this bay"))
                .toList();
        assertThat(uncovered).isNotEmpty();
        assertThat(uncovered).allMatch(complaint -> complaint.severity() == Severity.ADVISORY);
    }

    @Test
    void aJointOnAMemberOffItsPanelIsBlocking() {
        Joint stray = new Joint(
                JointKind.LAP, new Stringer(7, Side.LEFT), Sample.lap().rows());
        PanelBuild build = new PanelBuild(Sample.forwardUpper().panel(), Sample.milled()).withJoints(List.of(stray));
        List<Complaint> named = saying(
                Audits.auditAirframe(new Airframe("test", Sample.grid(), List.of(build))),
                "which is not on this panel");
        assertThat(named).hasSize(1);
        assertThat(named.get(0).severity()).isSameAs(Severity.BLOCKING);
    }

    @Test
    void aCutoutCentredOffItsPanelIsAdvisory() {
        Cutout stray = new Cutout(
                "W99L",
                CutoutKind.WINDOW,
                new Offset(inch("900.000"), inch("20.000")),
                inch("13.000"),
                inch("9.000"),
                inch("2.000"));
        PanelBuild build = new PanelBuild(Sample.forwardUpper().panel(), Sample.milled()).withCutouts(List.of(stray));
        List<Complaint> named = saying(
                Audits.auditAirframe(new Airframe("test", Sample.grid(), List.of(build))), "centred outside the panel");
        assertThat(named).hasSize(1);
        assertThat(named.get(0).severity()).isSameAs(Severity.ADVISORY);
    }

    @Test
    void twoDoublersSharingSkinAreAdvisory() {
        PanelBuild build = new PanelBuild(
                        Sample.forwardLower().panel(), Sample.forwardLower().thickness())
                .withDoublers(List.of(Sample.oldPatch(), Sample.secondPatch()));
        List<Complaint> named =
                saying(Audits.auditAirframe(new Airframe("test", Sample.grid(), List.of(build))), "share skin");
        assertThat(named).hasSize(1);
        assertThat(named.get(0).severity()).isSameAs(Severity.ADVISORY);
    }

    @Test
    void aPanelOnAMemberOffTheGridIsBlocking() {
        FuselageGrid whole = Sample.grid();
        FuselageGrid trimmed = new FuselageGrid(whole.frames().subList(0, 3), whole.stringers());
        List<Complaint> named = saying(
                Audits.auditAirframe(new Airframe("test", trimmed, List.of(Sample.forwardUpper()))),
                "is not on the grid");
        assertThat(named).isNotEmpty();
        assertThat(named).allMatch(complaint -> complaint.severity() == Severity.BLOCKING);
    }

    @Test
    void blockingFiltersOutTheAdvice() {
        List<Complaint> complaints = Audits.auditAirframe(Sample.airframe());
        assertThat(Complaint.blocking(complaints)).allMatch(complaint -> complaint.severity() == Severity.BLOCKING);
        assertThat(Complaint.blocking(complaints).size()).isLessThan(complaints.size());
    }

    @Test
    void complaintsComeBackSortedWithTheBlockingOnesFirst() {
        List<Complaint> complaints = Audits.auditAirframe(Sample.airframe());
        List<Integer> ranks = complaints.stream()
                .map(complaint -> complaint.severity().rank())
                .toList();
        List<Integer> sorted = new ArrayList<>(ranks);
        sorted.sort(null);
        assertThat(ranks).isEqualTo(sorted);
    }

    @Test
    void aComplaintReadsBackWhatItIsAbout() {
        Complaint complaint = new Complaint(Severity.BLOCKING, "5310-142L", "FR90 is not on the grid");
        assertThat(complaint).hasToString("[blocking] 5310-142L: FR90 is not on the grid");
        assertThat(Severity.BLOCKING.stopsWork()).isTrue();
        assertThat(Severity.ADVISORY.stopsWork()).isFalse();
    }

    @Test
    void aFrameOffTheGridIsStillAFrame() {
        assertThat(new Frame(90).number()).isEqualTo(90);
    }
}
