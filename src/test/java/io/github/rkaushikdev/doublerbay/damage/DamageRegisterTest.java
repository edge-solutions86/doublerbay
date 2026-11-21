package io.github.rkaushikdev.doublerbay.damage;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageRegisterTest {

    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Dent dent(String depth) {
        return new Dent(inch(depth), inch("3.000"), inch("2.000"));
    }

    private static Dent dent() {
        return dent("0.030");
    }

    private static Finding card(int sequence, Serial serial, DamageKind kind, Geometry geometry, LocalDate found) {
        return new Finding(
                new FindingId(2025, sequence),
                serial,
                kind,
                geometry != null ? geometry : dent(),
                Sample.point(),
                found,
                DiscoverySource.SCHEDULED_CHECK,
                kind == DamageKind.DENT ? DamageOrigin.IMPACT : DamageOrigin.FATIGUE,
                usage(13900, 24400));
    }

    private static Finding card(int sequence, Serial serial) {
        return card(sequence, serial, DamageKind.DENT, null, LocalDate.of(2025, 3, 14));
    }

    private static Finding card(int sequence, LocalDate found) {
        return card(sequence, MSN, DamageKind.DENT, null, found);
    }

    private static Finding card(int sequence) {
        return card(sequence, MSN);
    }

    private static Finding card() {
        return card(1);
    }

    private static DamageRegister register(Finding... cards) {
        DamageRegister held = new DamageRegister();
        for (Finding one : cards.length > 0 ? cards : new Finding[] {card()}) {
            held.raiseFinding(one);
        }
        return held;
    }

    private static List<Integer> sequences(Iterable<Finding> cards) {
        List<Integer> numbers = new ArrayList<>();
        cards.forEach(one -> numbers.add(one.reference().sequence()));
        return numbers;
    }

    private static Length depthOf(Finding card) {
        return ((Dent) card.geometry()).depth();
    }

    @Test
    void aRegisterCountsItsCardsAndHowManyAreOpen() {
        assertThat(register()).hasToString("1 cards, 1 open");
        assertThat(register().size()).isEqualTo(1);
        assertThat(register().contains(new FindingId(2025, 1))).isTrue();
    }

    @Test
    void aCardRaisedTwiceIsRefused() {
        DamageRegister held = register();
        assertThatThrownBy(() -> held.raiseFinding(card()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has already been raised");
    }

    @Test
    void aCardRaisedAtALaterRevisionIsRefused() {
        Finding later = card().reMeasured(dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        assertThatThrownBy(() -> new DamageRegister().raiseFinding(later))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is raised at revision one");
    }

    @Test
    void aCardThatIsNotThereIsALookupMiss() {
        assertThatThrownBy(() -> register().latest(new FindingId(2025, 99)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not a finding on this register");
    }

    @Test
    void aReMeasurementMakesARevisionAndTheEarlierOneStays() {
        DamageRegister held = register();
        held.reMeasure(new FindingId(2025, 1), dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        assertThat(depthOf(held.latest(new FindingId(2025, 1)))).isEqualTo(inch("0.040"));
        assertThat(held.history(new FindingId(2025, 1))).hasSize(2);
        assertThat(depthOf(held.history(new FindingId(2025, 1)).get(0))).isEqualTo(inch("0.030"));
    }

    @Test
    void theHistoryComesBackOldestFirst() {
        DamageRegister held = register();
        held.reMeasure(new FindingId(2025, 1), dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        held.reMeasure(new FindingId(2025, 1), dent("0.050"), LocalDate.of(2025, 9, 1), usage(14600, 25400));
        assertThat(held.history(new FindingId(2025, 1)).stream().map(Finding::revision))
                .containsExactly(1, 2, 3);
    }

    @Test
    void aCardStaysOneCardHoweverOftenItIsReMeasured() {
        DamageRegister held = register();
        held.reMeasure(new FindingId(2025, 1), dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        assertThat(held.size()).isEqualTo(1);
    }

    @Test
    void supersedingClosesACardAndNamesWhatReplacedIt() {
        DamageRegister held = register(card(1), card(2));
        Finding closed = held.supersede(new FindingId(2025, 1), new FindingId(2025, 2));
        assertThat(closed.isOpen()).isFalse();
        assertThat(held.latest(new FindingId(2025, 1)).supersededBy()).contains(new FindingId(2025, 2));
        assertThat(held).hasToString("2 cards, 1 open");
    }

    @Test
    void supersedingByACardThatIsNotThereIsRefused() {
        assertThatThrownBy(() -> register().supersede(new FindingId(2025, 1), new FindingId(2025, 99)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not a finding on this register");
    }

    @Test
    void aSupersededCardIsNotSupersededAgain() {
        DamageRegister held = register(card(1), card(2));
        held.supersede(new FindingId(2025, 1), new FindingId(2025, 2));
        assertThatThrownBy(() -> held.supersede(new FindingId(2025, 1), new FindingId(2025, 2)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has already been superseded");
    }

    @Test
    void aSupersededCardIsNotReMeasured() {
        DamageRegister held = register(card(1), card(2));
        held.supersede(new FindingId(2025, 1), new FindingId(2025, 2));
        assertThatThrownBy(() ->
                        held.reMeasure(new FindingId(2025, 1), dent("0.040"), LocalDate.of(2025, 6, 1), usage(1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not re measured");
    }

    @Test
    void theCardsOnOneTailAreTheCardsOnThatTail() {
        DamageRegister held = register(card(1), card(2, OTHER));
        assertThat(sequences(held.onTail(MSN))).containsExactly(1);
        assertThat(sequences(held.onTail(OTHER))).containsExactly(2);
    }

    @Test
    void theOpenListDropsWhatHasBeenSuperseded() {
        DamageRegister held = register(card(1), card(2));
        held.supersede(new FindingId(2025, 1), new FindingId(2025, 2));
        assertThat(sequences(held.openOn(MSN))).containsExactly(2);
    }

    @Test
    void cardsCanBePulledOutByKind() {
        Finding cracked =
                card(2, MSN, DamageKind.CRACK, new Crack(inch("0.750"), Angle.ofDegrees(0)), LocalDate.of(2025, 3, 14));
        DamageRegister held = register(card(1), cracked);
        assertThat(sequences(held.ofKind(DamageKind.CRACK))).containsExactly(2);
        assertThat(sequences(held.ofKind(DamageKind.DENT))).containsExactly(1);
    }

    @Test
    void cardsCanBePulledOutByTheWindowTheyWereRaisedIn() {
        DamageRegister held = register(card(1, LocalDate.of(2025, 1, 5)), card(2, LocalDate.of(2025, 6, 20)));
        List<Finding> found = held.foundBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1));
        assertThat(sequences(found)).containsExactly(1);
    }

    @Test
    void aWindowThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> register().foundBetween(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is before");
    }

    @Test
    void theWindowIsTakenOnWhenTheCardWasRaisedAndNotReMeasured() {
        DamageRegister held = register(card(1, LocalDate.of(2025, 1, 5)));
        held.reMeasure(new FindingId(2025, 1), dent("0.040"), LocalDate.of(2025, 9, 1), usage(14600, 25400));
        assertThat(held.foundBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1)))
                .hasSize(1);
        assertThat(held.foundBetween(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 10, 1)))
                .isEmpty();
    }

    @Test
    void theNextNumberCarriesOnFromTheHighestUsedThatYear() {
        DamageRegister held = register(card(1), card(7));
        assertThat(held.nextReference(2025)).isEqualTo(new FindingId(2025, 8));
        assertThat(held.nextReference(2026)).isEqualTo(new FindingId(2026, 1));
    }

    @Test
    void iteratingGivesTheLatestRevisionOfEveryCard() {
        DamageRegister held = register(card(1), card(2));
        held.reMeasure(new FindingId(2025, 1), dent("0.040"), LocalDate.of(2025, 6, 1), usage(14200, 24900));
        List<Length> depths = new ArrayList<>();
        held.forEach(one -> depths.add(depthOf(one)));
        assertThat(depths).contains(inch("0.040"));
        assertThat(depths).contains(inch("0.030"));
    }
}
