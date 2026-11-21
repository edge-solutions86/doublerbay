package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Station;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GridTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static FuselageGrid grid() {
        List<FrameStation> frames = new ArrayList<>();
        for (int number = 40; number < 46; number++) {
            frames.add(new FrameStation(new Frame(number), Station.ofInches(String.valueOf(600 + (number - 40) * 20))));
        }
        List<StringerLine> stringers = new ArrayList<>();
        for (int number = 0; number < 6; number++) {
            stringers.add(new StringerLine(
                    number != 0 ? new Stringer(number, Side.LEFT) : new Stringer(0), inch(String.valueOf(number * 8))));
        }
        return new FuselageGrid(frames, stringers);
    }

    private static FuselagePoint point(int frame, String aft, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inch(aft), new Stringer(stringer, Side.LEFT), inch(down));
    }

    private static FuselagePoint point() {
        return point(41, "12.500", 2, "3.000");
    }

    @Test
    void aGridHoldsBothLaddersInOrder() {
        assertThat(grid().stationOf(new Frame(41))).isEqualTo(Station.ofInches("620"));
        assertThat(grid().aroundOf(new Stringer(2, Side.LEFT))).isEqualTo(inch("16.000"));
    }

    @Test
    void aMemberOffTheGridIsALookupMiss() {
        assertThatThrownBy(() -> grid().stationOf(new Frame(90)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on this grid");
        assertThatThrownBy(() -> grid().aroundOf(new Stringer(40, Side.LEFT)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on this grid");
    }

    @Test
    void thePitchIsTheGapToTheNextMemberAftAndBelow() {
        assertThat(grid().framePitchAftOf(new Frame(41))).isEqualTo(inch("20.000"));
        assertThat(grid().stringerPitchBelow(new Stringer(2, Side.LEFT))).isEqualTo(inch("8.000"));
    }

    @Test
    void theLastMemberHasNothingPastIt() {
        assertThatThrownBy(() -> grid().framePitchAftOf(new Frame(45)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("no frame aft of it");
        assertThatThrownBy(() -> grid().stringerPitchBelow(new Stringer(5, Side.LEFT)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("no stringer below it");
    }

    @Test
    void aWrittenLocationResolvesToBarrelCoordinates() {
        assertThat(grid().absolute(point())).isEqualTo(new Offset(inch("632.500"), inch("19.000")));
    }

    @Test
    void aGridNeedsTwoOfEachToBoundAnything() {
        assertThatThrownBy(() -> new FuselageGrid(
                        List.of(new FrameStation(new Frame(41), Station.ofInches("620"))), grid().stringers()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least two frames");
        assertThatThrownBy(
                        () -> new FuselageGrid(grid().frames(), List.of(new StringerLine(new Stringer(0), inch("0")))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least two stringers");
    }

    @Test
    void framesHaveToBeListedForwardToAft() {
        List<FrameStation> reversedFrames = new ArrayList<>(grid().frames()).reversed();
        assertThatThrownBy(() -> new FuselageGrid(reversedFrames, grid().stringers()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("forward to aft");
    }

    @Test
    void twoFramesOnTheSameStationAreRefused() {
        List<FrameStation> frames = List.of(
                new FrameStation(new Frame(41), Station.ofInches("620")),
                new FrameStation(new Frame(42), Station.ofInches("620")));
        assertThatThrownBy(() -> new FuselageGrid(frames, grid().stringers()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no two on the same station");
    }

    @Test
    void stringersHaveToClimbFromTheCrown() {
        List<StringerLine> stringers = List.of(
                new StringerLine(new Stringer(0), inch("8.000")),
                new StringerLine(new Stringer(1, Side.LEFT), inch("4.000")));
        assertThatThrownBy(() -> new FuselageGrid(grid().frames(), stringers))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no two together");
    }

    @Test
    void aStringerAboveTheCrownIsRefused() {
        assertThatThrownBy(() -> new StringerLine(new Stringer(0), inch("-1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cannot be above the crown");
    }

    @Test
    void aPointFallsInTheBayThatBracketsIt() {
        assertThat(grid().bayOf(point())).hasToString("FR41-FR42 / S-2L-S-3L");
    }

    @Test
    void aPointMeasuredOffTheWrongFrameStillLandsInTheRightBay() {
        FuselagePoint farBack = point(40, "32.500", 2, "3.000");
        assertThat(grid().bayOf(farBack)).isEqualTo(grid().bayOf(point()));
    }

    @Test
    void aPointOnAMemberBelongsToTheBayAftAndBelowIt() {
        FuselagePoint onFrame = point(41, "0", 2, "0");
        assertThat(grid().bayOf(onFrame)).hasToString("FR41-FR42 / S-2L-S-3L");
    }

    @Test
    void aPointOffTheEndOfTheGridIsRefused() {
        assertThatThrownBy(() -> grid().bayOf(point(45, "30.000", 2, "3.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is off the frames");
        assertThatThrownBy(() -> grid().bayOf(point(41, "12.500", 5, "10.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is off the stringers");
    }

    @Test
    void theMarginIsTheNearestOfTheFourBoundingMembers() {
        Margin margin = grid().marginOf(point());
        assertThat(margin.member()).isEqualTo(new Stringer(2, Side.LEFT));
        assertThat(margin.distance()).isEqualTo(inch("3.000"));
        assertThat(margin).hasToString("3.000 off S-2L");
    }

    @Test
    void theMarginCanBeToAFrameInstead() {
        Margin margin = grid().marginOf(point(41, "2.000", 2, "4.000"));
        assertThat(margin.member()).isEqualTo(new Frame(41));
        assertThat(margin.distance()).isEqualTo(inch("2.000"));
    }

    @Test
    void theMarginCanBeToTheFarSideOfTheBay() {
        Margin margin = grid().marginOf(point(41, "19.000", 2, "4.000"));
        assertThat(margin.member()).isEqualTo(new Frame(42));
        assertThat(margin.distance()).isEqualTo(inch("1.000"));

        Margin below = grid().marginOf(point(41, "10.000", 2, "7.500"));
        assertThat(below.member()).isEqualTo(new Stringer(3, Side.LEFT));
        assertThat(below.distance()).isEqualTo(inch("0.500"));
    }

    @Test
    void twoCardsForTheSameDentNormaliseToTheSameRecord() {
        FuselagePoint writtenOff41 = point();
        FuselagePoint writtenOff40 = point(40, "32.500", 2, "3.000");
        assertThat(grid().normalised(writtenOff41)).isEqualTo(grid().normalised(writtenOff40));
        assertThat(writtenOff41).isNotEqualTo(writtenOff40);
    }

    @Test
    void aNormalisedPointIsMeasuredOffTheMembersThatBoundIt() {
        FuselagePoint normalised = grid().normalised(point(40, "32.500", 2, "3.000"));
        assertThat(normalised.frame()).isEqualTo(new Frame(41));
        assertThat(normalised.aftOfFrame()).isEqualTo(inch("12.500"));
        assertThat(normalised.stringer()).isEqualTo(new Stringer(2, Side.LEFT));
        assertThat(normalised.belowStringer()).isEqualTo(inch("3.000"));
    }

    @Test
    void normalisingSomethingAlreadyNormalChangesNothing() {
        assertThat(grid().normalised(point())).isEqualTo(point());
    }

    @Test
    void aMarginReadsBackWhatItIsOff() {
        assertThat(new Margin(new Frame(41), inch("2.000"))).hasToString("2.000 off FR41");
    }

    @Test
    void aFrameAndStringerLineReadBackWhereTheyAre() {
        assertThat(new FrameStation(new Frame(41), Station.ofInches("620"))).hasToString("FR41 at STA 620.000");
        assertThat(new StringerLine(new Stringer(2, Side.LEFT), inch("16"))).hasToString("S-2L at 16.000 round");
    }
}
