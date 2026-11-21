package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LatticeTest {

    private static Stringer left(int number) {
        return new Stringer(number, Side.LEFT);
    }

    private static Bay bay(int forward, int aft, Stringer upper, Stringer lower) {
        return new Bay(new Frame(forward), new Frame(aft), upper, lower);
    }

    private static Bay bay() {
        return bay(41, 42, left(14), left(15));
    }

    @Test
    void aStringerCarriesANumberAndAHand() {
        Stringer stringer = new Stringer(14, Side.LEFT);
        assertThat(stringer).hasToString("S-14L");
        assertThat(stringer.side()).isSameAs(Side.LEFT);
    }

    @Test
    void theCrownStringerHasNoHand() {
        assertThat(new Stringer(0)).hasToString("S-0");
        assertThat(new Stringer(0).isCrown()).isTrue();
        assertThatThrownBy(() -> new Stringer(0, Side.LEFT))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("crown stringer has no hand");
    }

    @Test
    void aStringerOffTheCrownHasToSayWhichHand() {
        assertThatThrownBy(() -> new Stringer(14))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has to say which hand");
    }

    @Test
    void aStringerNumberOffTheBarrelIsRefused() {
        assertThatThrownBy(() -> new Stringer(60, Side.LEFT))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not on this barrel");
        assertThatThrownBy(() -> new Stringer(-1, Side.LEFT))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not on this barrel");
    }

    static Stream<Arguments> writtenStringers() {
        return Stream.of(
                Arguments.of("S-14L", new Stringer(14, Side.LEFT)),
                Arguments.of("S14R", new Stringer(14, Side.RIGHT)),
                Arguments.of("s-4l", new Stringer(4, Side.LEFT)),
                Arguments.of(" S-0 ", new Stringer(0)));
    }

    @ParameterizedTest
    @MethodSource("writtenStringers")
    void theStringerParserReadsTheWrittenForm(String text, Stringer expected) {
        assertThat(Stringer.parse(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "14L", "S-", "SL", "S-100L"})
    void theStringerParserRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> Stringer.parse(text)).isInstanceOf(BadReading.class);
    }

    @Test
    void aHandedStringerWrittenWithoutAHandIsRefused() {
        assertThatThrownBy(() -> Stringer.parse("S-14"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not say which hand");
    }

    @Test
    void theLowerNumberIsTheHigherStringer() {
        assertThat(left(4).isAbove(left(22))).isTrue();
        assertThat(left(22).isAbove(left(4))).isFalse();
    }

    @Test
    void mirroringSwapsTheHandAndLeavesTheCrownAlone() {
        assertThat(left(14).mirrored()).isEqualTo(new Stringer(14, Side.RIGHT));
        assertThat(new Stringer(0).mirrored()).isEqualTo(new Stringer(0));
    }

    @Test
    void stringersSpanWithinAHandAndNotAcross() {
        assertThat(left(4).spansTo(left(9))).isEqualTo(5);
        assertThat(new Stringer(0).spansTo(left(4))).isEqualTo(4);
        assertThatThrownBy(() -> left(4).spansTo(new Stringer(4, Side.RIGHT)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("opposite hands do not span");
    }

    @Test
    void aFrameReadsAndPrintsItsNumber() {
        assertThat(Frame.parse("FR42")).isEqualTo(new Frame(42));
        assertThat(Frame.parse("FS 42")).isEqualTo(new Frame(42));
        assertThat(new Frame(42)).hasToString("FR42");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "42", "FRAME 42", "FR", "FR1234"})
    void theFrameParserRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> Frame.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a frame");
    }

    @Test
    void framesStartAtOne() {
        assertThatThrownBy(() -> new Frame(0)).isInstanceOf(BadReading.class).hasMessageContaining("start at one");
    }

    @Test
    void framesOrderAndStepAft() {
        assertThat(new Frame(41).isLessThan(new Frame(42))).isTrue();
        assertThat(new Frame(41)).isLessThan(new Frame(42));
        assertThat(new Frame(41).nextAft()).isEqualTo(new Frame(42));
        assertThat(new Frame(41).spansTo(new Frame(45))).isEqualTo(4);
    }

    @Test
    void aBayIsBoundedByTwoFramesAndTwoStringers() {
        assertThat(bay()).hasToString("FR41-FR42 / S-14L-S-15L");
        assertThat(bay().side()).isSameAs(Side.LEFT);
        assertThat(bay().isSingle()).isTrue();
    }

    @Test
    void aBayThatSpansMoreThanOnePitchIsNotASingleBay() {
        Bay wide = bay(41, 44, left(14), left(18));
        assertThat(wide.framePitches()).isEqualTo(3);
        assertThat(wide.stringerPitches()).isEqualTo(4);
        assertThat(wide.isSingle()).isFalse();
    }

    @Test
    void aBayHasToBeGivenItsBoundsTheRightWayRound() {
        assertThatThrownBy(() -> bay(42, 41, left(14), left(15)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not forward of");
        assertThatThrownBy(() -> bay(41, 42, left(15), left(14)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not above");
    }

    @Test
    void aBayDoesNotStraddleTheCrown() {
        assertThatThrownBy(() -> new Bay(new Frame(41), new Frame(42), left(2), new Stringer(3, Side.RIGHT)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not straddle the crown");
    }

    @Test
    void aBayMayHangOffTheCrownStringer() {
        Bay crownBay = new Bay(new Frame(41), new Frame(42), new Stringer(0), left(1));
        assertThat(crownBay.side()).isSameAs(Side.LEFT);
        assertThat(crownBay.isSingle()).isTrue();
    }
}
