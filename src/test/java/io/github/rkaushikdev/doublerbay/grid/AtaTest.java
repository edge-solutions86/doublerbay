package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AtaTest {

    @Test
    void aChapterPrintsAsTwoDigits() {
        assertThat(Chapter.FUSELAGE).hasToString("53");
        assertThat(Chapter.FUSELAGE.number()).isEqualTo(53);
        assertThat(Chapter.STANDARD_PRACTICES.title()).isEqualTo("standard practices");
    }

    @Test
    void aReferencePrintsPadded() {
        assertThat(new Reference(Chapter.FUSELAGE, 10, 3)).hasToString("53-10-03");
        assertThat(new Reference(Chapter.WINGS, 5)).hasToString("57-05");
    }

    static Stream<Arguments> printedForms() {
        return Stream.of(
                Arguments.of("53-10-03", new Reference(Chapter.FUSELAGE, 10, 3)),
                Arguments.of("53-10", new Reference(Chapter.FUSELAGE, 10)),
                Arguments.of(" 57-20-01 ", new Reference(Chapter.WINGS, 20, 1)),
                Arguments.of("51-00", new Reference(Chapter.STANDARD_PRACTICES, 0)));
    }

    @ParameterizedTest
    @MethodSource("printedForms")
    void theParserReadsThePrintedForm(String text, Reference expected) {
        assertThat(Reference.parse(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "53", "5-10", "53-1", "53-10-3", "53/10/03", "53-10-03-01"})
    void theParserRefusesAMalformedReference(String text) {
        assertThatThrownBy(() -> Reference.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a manual reference");
    }

    @Test
    void aChapterOffTheShelfIsRefused() {
        assertThatThrownBy(() -> Reference.parse("34-10-01"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not one this desk reads");
    }

    @Test
    void aSectionOutOfRangeIsRefused() {
        assertThatThrownBy(() -> new Reference(Chapter.FUSELAGE, 100))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a section is two digits");
        assertThatThrownBy(() -> new Reference(Chapter.FUSELAGE, 10, 100))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a subject is two digits");
    }

    @Test
    void aReferenceWithNoSubjectNamesAWholeSection() {
        assertThat(new Reference(Chapter.FUSELAGE, 10).isSection()).isTrue();
        assertThat(new Reference(Chapter.FUSELAGE, 10, 3).isSection()).isFalse();
    }

    @Test
    void aSectionCoversEverySubjectInsideIt() {
        Reference section = new Reference(Chapter.FUSELAGE, 10);
        assertThat(section.covers(new Reference(Chapter.FUSELAGE, 10, 3))).isTrue();
        assertThat(section.covers(new Reference(Chapter.FUSELAGE, 10, 99))).isTrue();
        assertThat(section.covers(section)).isTrue();
    }

    @Test
    void aSubjectCoversOnlyItself() {
        Reference subject = new Reference(Chapter.FUSELAGE, 10, 3);
        assertThat(subject.covers(subject)).isTrue();
        assertThat(subject.covers(new Reference(Chapter.FUSELAGE, 10, 4))).isFalse();
        assertThat(subject.covers(new Reference(Chapter.FUSELAGE, 10))).isFalse();
    }

    @Test
    void nothingCoversAcrossAChapterOrASection() {
        assertThat(new Reference(Chapter.FUSELAGE, 10).covers(new Reference(Chapter.WINGS, 10, 3)))
                .isFalse();
        assertThat(new Reference(Chapter.FUSELAGE, 10).covers(new Reference(Chapter.FUSELAGE, 20, 3)))
                .isFalse();
    }

    @Test
    void droppingTheSubjectGivesTheSectionItIsFiledUnder() {
        assertThat(new Reference(Chapter.FUSELAGE, 10, 3).sectionOnly()).isEqualTo(new Reference(Chapter.FUSELAGE, 10));
    }

    @Test
    void referencesSortWithTheSectionAheadOfItsSubjects() {
        List<Reference> ordered = new ArrayList<>(List.of(
                new Reference(Chapter.WINGS, 10, 1),
                new Reference(Chapter.FUSELAGE, 10, 3),
                new Reference(Chapter.FUSELAGE, 10),
                new Reference(Chapter.FUSELAGE, 5, 1)));
        ordered.sort(null);
        assertThat(ordered.stream().map(Reference::toString).toList())
                .containsExactly("53-05-01", "53-10", "53-10-03", "57-10-01");
    }

    @Test
    void referencesHashByValue() {
        assertThat(new HashSet<>(List.of(Reference.parse("53-10-03"), new Reference(Chapter.FUSELAGE, 10, 3))))
                .hasSize(1);
    }

    @Test
    void theDeskOwnsSkinAndNotHardware() {
        assertThat(Chapter.isSkinChapter(Chapter.FUSELAGE)).isTrue();
        assertThat(Chapter.isSkinChapter(Chapter.WINGS)).isTrue();
        assertThat(Chapter.isSkinChapter(Chapter.DOORS)).isFalse();
        assertThat(Chapter.isSkinChapter(Chapter.STANDARD_PRACTICES)).isFalse();
        assertThat(Chapter.SKIN_CHAPTERS).doesNotContain(Chapter.WINDOWS);
    }
}
