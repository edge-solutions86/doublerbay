package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.Effectivity;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentTest {

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static Revision revision() {
        return new Revision(12, date(2024, 9, 1), date(2024, 12, 1));
    }

    private static Manual manual(Revision... revisions) {
        if (revisions.length == 0) {
            revisions = new Revision[] {
                new Revision(10, date(2023, 3, 1), date(2023, 6, 1)),
                new Revision(11, date(2023, 12, 1), date(2024, 3, 1)),
                new Revision(12, date(2024, 9, 1), date(2024, 12, 1)),
            };
        }
        return new Manual(Chapter.FUSELAGE, List.of(revisions));
    }

    private static Page page(String reference, int revisionNumber, Integer withdrawn) {
        return Page.of(
                Reference.parse(reference),
                "allowable damage, fuselage skin",
                revisionNumber,
                Effectivity.allAircraft(),
                withdrawn);
    }

    private static Page page(int revisionNumber, Integer withdrawn) {
        return page("53-10-03", revisionNumber, withdrawn);
    }

    private static Page page(int revisionNumber) {
        return page(revisionNumber, null);
    }

    private static Page page() {
        return page(10);
    }

    @Test
    void aRevisionReadsBackWhenItStartedToGovern() {
        assertThat(revision()).hasToString("rev 12 of 2024-12-01");
        assertThat(revision().leadDays()).isEqualTo(91);
    }

    @Test
    void aRevisionThatTakesEffectBeforeItWasIssuedIsRefused() {
        assertThatThrownBy(() -> new Revision(12, date(2024, 12, 1), date(2024, 9, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("takes effect before it was issued");
    }

    @Test
    void revisionsAreNumberedFromOne() {
        assertThatThrownBy(() -> new Revision(0, date(2024, 9, 1), date(2024, 12, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("numbered from one");
    }

    @Test
    void aRevisionIsNotInForceBeforeItsEffectiveDate() {
        assertThat(revision().inForceOn(date(2024, 11, 30))).isFalse();
        assertThat(revision().inForceOn(date(2024, 12, 1))).isTrue();
        assertThat(revision().inForceOn(date(2026, 1, 1))).isTrue();
    }

    @Test
    void aChapterReadsBackItsLatestRevision() {
        assertThat(manual()).hasToString("chapter 53, rev 12 of 2024-12-01");
        assertThat(manual().latest().number()).isEqualTo(12);
        assertThat(manual().first().number()).isEqualTo(10);
    }

    @Test
    void aChapterNeedsAtLeastOneRevision() {
        assertThatThrownBy(() -> new Manual(Chapter.FUSELAGE, List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one revision");
    }

    @Test
    void revisionsHaveToBeListedInOrder() {
        assertThatThrownBy(() -> manual(
                        new Revision(12, date(2024, 9, 1), date(2024, 12, 1)),
                        new Revision(11, date(2023, 12, 1), date(2024, 3, 1))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("in order with no repeats");
    }

    @Test
    void twoRevisionsTakingEffectOnOneDayAreRefused() {
        assertThatThrownBy(() -> manual(
                        new Revision(11, date(2023, 12, 1), date(2024, 3, 1)),
                        new Revision(12, date(2024, 1, 1), date(2024, 3, 1))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no two on one day");
    }

    @Test
    void theRevisionInForceIsTheLastOneToHaveStarted() {
        assertThat(manual().inForceOn(date(2024, 6, 1)).number()).isEqualTo(11);
        assertThat(manual().inForceOn(date(2024, 12, 1)).number()).isEqualTo(12);
        assertThat(manual().inForceOn(date(2026, 1, 1)).number()).isEqualTo(12);
    }

    @Test
    void theDayBeforeARevisionStartsThePreviousOneStillGoverns() {
        assertThat(manual().inForceOn(date(2024, 11, 30)).number()).isEqualTo(11);
    }

    @Test
    void aDayBeforeTheFirstRevisionHasNothingInForce() {
        assertThatThrownBy(() -> manual().inForceOn(date(2020, 1, 1)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("had no revision in force");
    }

    @Test
    void aRevisionCanBeAskedForByNumber() {
        assertThat(manual().numbered(11).effective()).isEqualTo(date(2024, 3, 1));
        assertThatThrownBy(() -> manual().numbered(99))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no revision 99");
    }

    @Test
    void issuingARevisionLeavesTheEarlierChapterAlone() {
        Manual before = manual();
        Manual after = before.issued(new Revision(13, date(2025, 6, 1), date(2025, 9, 1)));
        assertThat(after.latest().number()).isEqualTo(13);
        assertThat(before.latest().number()).isEqualTo(12);
    }

    @Test
    void aPageReadsBackItsReferenceAndTitle() {
        assertThat(page()).hasToString("53-10-03 allowable damage, fuselage skin");
    }

    @Test
    void aPageNeedsATitleAndARevision() {
        assertThatThrownBy(() -> new Page(Reference.parse("53-10-03"), "  ", 10))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a title");
        assertThatThrownBy(() -> new Page(Reference.parse("53-10-03"), "allowable damage", 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is issued at a revision");
    }

    @Test
    void aPageIsNotInTheBookBeforeItWasIssued() {
        assertThat(page(11).isLiveAt(10)).isFalse();
        assertThat(page(11).isLiveAt(11)).isTrue();
        assertThat(page(11).isLiveAt(12)).isTrue();
    }

    @Test
    void aWithdrawnPageLeavesTheBookAtTheRevisionThatWithdrewIt() {
        Page withdrawn = page(10, 12);
        assertThat(withdrawn.isLiveAt(11)).isTrue();
        assertThat(withdrawn.isLiveAt(12)).isFalse();
        assertThat(withdrawn.isLiveAt(13)).isFalse();
    }

    @Test
    void aPageWithdrawnBeforeItWasIssuedIsRefused() {
        assertThatThrownBy(() -> page(12, 12))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("withdrawn before it was issued");
    }
}
