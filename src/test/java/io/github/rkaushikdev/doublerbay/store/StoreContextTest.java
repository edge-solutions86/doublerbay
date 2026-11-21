package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreContextTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final Serial MSN = new Serial("5127");

    private Database database;

    private static UtilisationLog logWithTwoReadings() {
        return UtilisationLog.opened(MSN, LocalDate.of(2016, 4, 12))
                .posted(new Snapshot(LocalDate.of(2024, 3, 14), Sample.flown(12000, 21000)))
                .posted(new Snapshot(LocalDate.of(2025, 3, 1), Sample.flown(13800, 24200)));
    }

    @BeforeEach
    void oneTailWithTwoReadings() {
        database = Databases.fresh();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(logWithTwoReadings());
        });
    }

    private TailContext built(LocalDate on, Serial serial) {
        return UnitOfWork.apply(database, unit -> StoreContext.contextFor(unit, new TypeCatalogue(), serial, on));
    }

    private TailContext built(LocalDate on) {
        return built(on, MSN);
    }

    private TailContext built() {
        return built(DAY);
    }

    @Test
    void theContextCarriesTheAeroplane() {
        assertThat(built().serial()).isEqualTo(MSN);
    }

    @Test
    void theContextCarriesTheDayItWasAskedFor() {
        assertThat(built().on()).isEqualTo(DAY);
    }

    @Test
    void theCountersComeOffTheLogAtThatDay() {
        assertThat(built().flown().cycles().count()).isEqualTo(13800);
    }

    @Test
    void anEarlierDayGetsTheEarlierCounters() {
        assertThat(built(LocalDate.of(2024, 6, 1)).flown().cycles().count()).isEqualTo(12000);
    }

    @Test
    void theStructureDefinitionComesOffTheSeries() {
        assertThat(built().barrel()).isNotNull();
        assertThat(built().wingBox()).isNotNull();
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsRefused() {
        assertThatThrownBy(() -> built(DAY, new Serial("9999"))).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aDayBeforeDeliveryIsRefused() {
        assertThatThrownBy(() -> built(LocalDate.of(2010, 1, 1)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not in service on 2010-01-01");
    }

    @Test
    void theDayOfDeliveryItselfIsInService() {
        assertThat(built(LocalDate.of(2016, 4, 12)).on()).isEqualTo(LocalDate.of(2016, 4, 12));
    }

    @Test
    void theAgeComesOffTheDeliveryDate() {
        assertThat(built().ageYears()).isGreaterThan(BigDecimal.valueOf(8));
    }
}
