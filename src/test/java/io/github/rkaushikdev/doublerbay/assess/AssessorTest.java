package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.v320.Manual57;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AssessorTest {

    private static final Book BOOK = Book.v320();

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static FuselagePoint inTheBay() {
        return Sample.cabinPointOn(10, "4.000");
    }

    private static Finding dentCard(String depth, String along, String around, boolean sharp, int sequence) {
        return Sample.cardWith()
                .sequence(sequence)
                .geometry(new Dent(inch(depth), inch(along), inch(around), sharp, false))
                .at(inTheBay())
                .build();
    }

    private static Finding dentCard(String depth, String along, String around) {
        return dentCard(depth, along, around, false, 1);
    }

    private static Finding dentCard(int sequence, String depth, String along, String around) {
        return dentCard(depth, along, around, false, sequence);
    }

    private static Finding dentCard(int sequence) {
        return dentCard(sequence, "0.020", "3.000", "2.000");
    }

    private static Finding dentCard() {
        return dentCard(1);
    }

    /** A point on the full thickness land, where a blend has room to work. */
    private static FuselagePoint onTheLand() {
        return Sample.cabinPointOn(10, "0.000");
    }

    private static Finding scratchCard(String depth, FuselagePoint at) {
        return Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch(depth), inch("2.000"), inch("0.060"), Angle.ofDegrees(0)))
                .at(at)
                .build();
    }

    private static Finding scratchCard(String depth) {
        return scratchCard(depth, onTheLand());
    }

    private static Assessment assessed(Finding finding) {
        return Assessor.assess(BOOK, Sample.tailContext(), finding);
    }

    private static Set<String> rules(List<Reason> reasons) {
        return reasons.stream().map(Reason::rule).collect(Collectors.toSet());
    }

    @Test
    void aSmallSmoothDentInOpenSkinIsAllowable() {
        Assessment answer = assessed(dentCard("0.010", "6.000", "4.000"));
        assertThat(answer.disposition()).isSameAs(Disposition.ALLOWABLE);
        assertThat(answer.needsWork()).isFalse();
    }

    @Test
    void theVerdictCitesThePageAndTheRevisionItCameOff() {
        Assessment answer = assessed(dentCard("0.010", "6.000", "4.000"));
        assertThat(answer.verdict().reference().orElseThrow()).hasToString("53-10-01");
        assertThat(answer.verdict().revision()).contains(12);
    }

    @Test
    void aDeepDentGoesToARepairBecauseThereIsNothingToGrind() {
        Assessment answer = assessed(dentCard("0.090", "6.000", "4.000"));
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
        assertThat(rules(answer.verdict().failures())).contains("depth");
    }

    @Test
    void aSharpDentGoesToARepairHoweverShallowItIs() {
        Assessment answer = assessed(dentCard("0.010", "0.150", "0.120"));
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
        assertThat(rules(answer.verdict().failures())).contains("sharpness");
    }

    @Test
    void aCrackedDentGoesToARepair() {
        Finding cracked = Sample.cardWith()
                .geometry(new Dent(inch("0.010"), inch("6.000"), inch("4.000"), false, true))
                .build();
        Assessment answer = assessed(cracked);
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void everyRuleRunsAndNotJustTheOnesUpToTheFirstNo() {
        Assessment answer = assessed(dentCard("0.090", "0.200", "0.180"));
        Set<String> rules = rules(answer.verdict().reasons());
        assertThat(rules).contains("depth");
        assertThat(rules).contains("sharpness");
        assertThat(rules).contains("smoothness");
    }

    @Test
    void theWorstAnswerStandsAndNotTheLastOneThatRan() {
        Assessment answer = assessed(dentCard("0.090", "6.000", "4.000"));
        assertThat(answer.verdict().passes()).isNotEmpty();
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aShallowScratchInOpenSkinIsAllowable() {
        Assessment answer = assessed(scratchCard("0.001"));
        assertThat(answer.disposition()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void aScratchOverTheCapIsReworkableBecauseItCanBeGroundOut() {
        Assessment answer = assessed(scratchCard("0.003"));
        assertThat(answer.disposition()).isSameAs(Disposition.REWORKABLE);
    }

    @Test
    void aScratchTooDeepToGrindOutIsARepairInstead() {
        Assessment answer = assessed(scratchCard("0.008"));
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
        assertThat(rules(answer.verdict().failures())).contains("residual");
    }

    @Test
    void theSameScratchOnAFastenerRowIsARepair() {
        Finding onARow = scratchCard("0.003", Sample.cabinPointOn(8, "0.500"));
        Assessment answer = assessed(onARow);
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aCrackIsARepairAtAnyLength() {
        Finding cracked = Sample.cardWith()
                .kind(DamageKind.CRACK)
                .geometry(new Crack(inch("0.100"), Angle.ofDegrees(0)))
                .at(inTheBay())
                .origin(DamageOrigin.FATIGUE)
                .build();
        Assessment answer = assessed(cracked);
        assertThat(answer.disposition()).isSameAs(Disposition.REPAIRABLE);
        assertThat(rules(answer.verdict().failures())).contains("allowable");
    }

    @Test
    void exfoliatingCorrosionIsARepairAtAnyDepth() {
        Finding lifted = Sample.cardWith()
                .kind(DamageKind.CORROSION)
                .geometry(new Corrosion(inch("1.000"), inch("1.000"), inch("0.001"), false, true))
                .at(inTheBay())
                .origin(DamageOrigin.ENVIRONMENT)
                .build();
        assertThat(assessed(lifted).disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aWingLowerDentIsARepairWhereTheSameDentOnTheBarrelIsNot() {
        Assessment barrel = assessed(dentCard("0.010", "6.000", "4.000"));
        Assessment wing = assessed(Sample.cardWith()
                .geometry(new Dent(inch("0.010"), inch("6.000"), inch("4.000")))
                .at(Sample.lowerWingPoint())
                .build());
        assertThat(barrel.disposition()).isSameAs(Disposition.ALLOWABLE);
        assertThat(wing.disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aChapterThatIsNotOnTheShelfLandsOutsideTheManual() {
        Book wingOnly = new Book();
        wingOnly.shelve(Manual57.manual(), Manual57.PAGES, Manual57::allowableDamage);
        Assessment answer = Assessor.assess(wingOnly, Sample.tailContext(), dentCard());
        assertThat(answer.disposition()).isSameAs(Disposition.OUT_OF_LIMITS);
        assertThat(answer.row()).isEmpty();
        assertThat(answer.verdict().reference()).isEmpty();
        assertThat(answer.verdict().why()).contains("not on the shelf");
    }

    @Test
    void aCardAssessedBeforeTheCampaignGetsTheLooserLimit() {
        TailContext early = Sample.tailContext(LocalDate.of(2024, 6, 1));
        TailContext late = Sample.tailContext(LocalDate.of(2025, 3, 14));
        Finding card = dentCard("0.070", "6.000", "4.000");
        assertThat(Assessor.assess(BOOK, early, card).disposition()).isSameAs(Disposition.ALLOWABLE);
        assertThat(Assessor.assess(BOOK, late, card).disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void anAssessmentReadsBackTheCardThePanelAndTheAnswer() {
        String printed = assessed(dentCard("0.010", "6.000", "4.000")).toString();
        assertThat(printed).startsWith("DR-2025-000001 on 5310-");
        assertThat(printed).contains("allowable");
    }

    @Test
    void aRunOfCardsComesBackInTheOrderTheyWereRaised() {
        List<Finding> cards = List.of(dentCard(3), dentCard(1), dentCard(2));
        List<Assessment> answers = Assessor.assessAll(BOOK, Sample.tailContext(), cards);
        assertThat(answers.stream().map(one -> one.finding().reference().sequence()))
                .containsExactly(1, 2, 3);
    }

    @Test
    void theWorstOfARunIsWhatThePanelReports() {
        List<Finding> cards = List.of(dentCard(1, "0.010", "6.000", "4.000"), dentCard(2, "0.090", "6.000", "4.000"));
        List<Assessment> answers = Assessor.assessAll(BOOK, Sample.tailContext(), cards);
        assertThat(Assessor.worstDisposition(answers)).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void nothingOpenReportsAsAllowable() {
        assertThat(Assessor.worstDisposition(List.of())).isSameAs(Disposition.ALLOWABLE);
    }
}
