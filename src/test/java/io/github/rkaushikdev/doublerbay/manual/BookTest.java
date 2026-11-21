package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Effectivity;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.v320.Manual53;
import io.github.rkaushikdev.doublerbay.v320.Manual57;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BookTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Aircraft tail() {
        return tail("5127");
    }

    private static Aircraft tail(String serial) {
        return new Aircraft(
                new Serial(serial), new Registration("N412VD"), Series.STANDARD, 1140, LocalDate.of(2016, 4, 12));
    }

    private static LimitTable blockedTable() {
        return new LimitTable(
                Reference.parse("53-10"),
                12,
                List.of(
                        new LimitRow(
                                new LimitKey(DamageKind.DENT),
                                LimitSet.allowable().withMaxDepth(inch("0.060")),
                                Reference.parse("53-10-01"),
                                Effectivity.allAircraft().withSerials(5001, 5240)),
                        new LimitRow(
                                new LimitKey(DamageKind.SCRATCH),
                                LimitSet.allowable().withMaxDepth(inch("0.005")),
                                Reference.parse("53-20-01"))));
    }

    @Test
    void theBookHoldsBothSkinChapters() {
        Book book = Book.v320();
        assertThat(book.size()).isEqualTo(2);
        assertThat(book.chapters()).isEqualTo(Set.of(Chapter.FUSELAGE, Chapter.WINGS));
        assertThat(book).hasToString("2 chapters on the shelf");
    }

    @Test
    void aChapterThatIsNotOnTheShelfIsALookupMiss() {
        assertThatThrownBy(() -> Book.v320().holding(Chapter.STABILISERS))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the shelf");
    }

    @Test
    void aChapterShelvedTwiceIsRefused() {
        Book book = Book.v320();
        assertThatThrownBy(() -> book.shelve(Manual53.manual(), Manual53.PAGES, Manual53::allowableDamage))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is already on the shelf");
    }

    @Test
    void aPageFromAnotherChapterIsRefused() {
        Book book = new Book();
        assertThatThrownBy(() -> book.shelve(Manual53.manual(), Manual57.PAGES, Manual53::allowableDamage))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a page of chapter 53");
    }

    @Test
    void theRevisionInForceComesOffTheDay() {
        Book book = Book.v320();
        assertThat(book.revisionOn(Chapter.FUSELAGE, LocalDate.of(2024, 6, 1)).number())
                .isEqualTo(11);
        assertThat(book.revisionOn(Chapter.FUSELAGE, LocalDate.of(2025, 3, 14)).number())
                .isEqualTo(12);
    }

    @Test
    void theTwoChaptersAreOnDifferentRevisionsAtTheSameTime() {
        Book book = Book.v320();
        assertThat(book.revisionOn(Chapter.FUSELAGE, DAY).number()).isEqualTo(12);
        assertThat(book.revisionOn(Chapter.WINGS, DAY).number()).isEqualTo(9);
    }

    @Test
    void theTableThatAnswersIsTheOneInForceThatDay() {
        Book book = Book.v320();
        LimitTable before = book.tableOn(Chapter.FUSELAGE, LocalDate.of(2024, 6, 1));
        LimitTable after = book.tableOn(Chapter.FUSELAGE, DAY);
        assertThat(before.revision()).isEqualTo(11);
        assertThat(after.revision()).isEqualTo(12);
    }

    @Test
    void aLimitReadBeforeTheCampaignIsTheLooserOne() {
        Book book = Book.v320();
        LimitRow before = book.limitsFor(
                Chapter.FUSELAGE,
                LocalDate.of(2024, 6, 1),
                tail(),
                DamageKind.DENT,
                PanelKind.PRESSURISED_SKIN,
                Region.OPEN_FIELD);
        LimitRow after = book.limitsFor(
                Chapter.FUSELAGE, DAY, tail(), DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD);
        assertThat(before.limits().maxDepth()).contains(inch("0.080"));
        assertThat(after.limits().maxDepth()).contains(inch("0.060"));
    }

    @Test
    void aDayBeforeTheFirstRevisionHasNoBookToRead() {
        assertThatThrownBy(() -> Book.v320().tableOn(Chapter.FUSELAGE, LocalDate.of(2019, 1, 1)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("had no revision in force");
    }

    @Test
    void thePagesInTheBookAreTheOnesLiveAtThatRevision() {
        Book book = Book.v320();
        List<Page> early = book.pagesOn(Chapter.FUSELAGE, LocalDate.of(2022, 6, 1));
        List<Page> later = book.pagesOn(Chapter.FUSELAGE, DAY);
        assertThat(early.size()).isLessThan(later.size());
    }

    @Test
    void aPageCanBePulledOutByItsReference() {
        Page found = Book.v320().page(Reference.parse("53-10-01"), DAY);
        assertThat(found.title()).startsWith("allowable damage");
    }

    @Test
    void aPageNotInTheBookThatDayIsALookupMiss() {
        assertThatThrownBy(() -> Book.v320().page(Reference.parse("53-30-02"), LocalDate.of(2022, 6, 1)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not in the book");
    }

    @Test
    void aRowWrittenForABlockOfSerialsDropsOutForATailOutsideIt() {
        LimitTable inside = blockedTable().effectiveFor(tail("5127"));
        LimitTable outside = blockedTable().effectiveFor(tail("5301"));
        assertThat(inside.size()).isEqualTo(2);
        assertThat(outside.size()).isEqualTo(1);
    }

    @Test
    void aTailOutsideTheBlockGetsALookupMissAndNotTheWrongRow() {
        LimitTable outside = blockedTable().effectiveFor(tail("5301"));
        assertThatThrownBy(() -> outside.lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no row for dent");
    }

    @Test
    void aTableWithNothingLeftAfterFilteringIsALookupMiss() {
        LimitTable onlyABlock = new LimitTable(
                Reference.parse("53-10"),
                12,
                List.of(new LimitRow(
                        new LimitKey(DamageKind.DENT),
                        LimitSet.allowable().withMaxDepth(inch("0.060")),
                        Reference.parse("53-10-01"),
                        Effectivity.allAircraft().withSerials(5001, 5240))));
        assertThatThrownBy(() -> onlyABlock.effectiveFor(tail("5301")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no rows that apply");
    }

    @Test
    void theBookCarriesTheCleanupAndInteractionRulesAlongside() {
        Book book = Book.v320();
        assertThat(book.cleanup().covers(DamageKind.SCRATCH)).isTrue();
        assertThat(book.interaction().counts().perPanel()).contains(6);
    }

    @Test
    void aHoldingReadsBackWhatItIs() {
        String printed = Book.v320().holding(Chapter.FUSELAGE).toString();
        assertThat(printed).startsWith("chapter 53, 5 pages");
    }
}
