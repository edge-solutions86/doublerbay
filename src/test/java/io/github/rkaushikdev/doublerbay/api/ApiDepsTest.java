package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

class ApiDepsTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);

    private static final Path SOURCE = Path.of("src/main/java/io/github/rkaushikdev/doublerbay/api/Dependencies.java");

    /** Enough of a request for the dependency, without standing a server up. */
    private static MockHttpServletRequest requestTo(Deps held) {
        StaticWebApplicationContext app = new StaticWebApplicationContext();
        app.getBeanFactory().registerSingleton("deps", held);
        app.refresh();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, app);
        return request;
    }

    private static Deps built() {
        return built(DAY);
    }

    private static Deps built(LocalDate today) {
        return Deps.of(Databases.empty(), Book.v320(), new TypeCatalogue(), today, null);
    }

    @Test
    void theDependencyComesOffTheApplication() {
        Deps held = built();
        assertThat(Dependencies.deps(requestTo(held))).isSameAs(held);
    }

    @Test
    void aReportNumberParses() {
        assertThat(Dependencies.referenceFrom("DR-2025-000041")).isEqualTo(new FindingId(2025, 41));
    }

    @Test
    void somethingThatIsNotAReportNumberIsRefused() {
        assertThatThrownBy(() -> Dependencies.referenceFrom("nonsense"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a damage report number");
    }

    @Test
    void aRepairNumberParses() {
        assertThat(Dependencies.repairFrom("RP-53-0001")).isEqualTo(new RepairId(53, 1));
    }

    @Test
    void somethingThatIsNotARepairNumberIsRefused() {
        assertThatThrownBy(() -> Dependencies.repairFrom("nonsense")).isInstanceOf(BadReading.class);
    }

    @Test
    void anOrderNumberParses() {
        assertThat(Dependencies.orderFrom("EO-2025-0001")).isEqualTo(new OrderId(2025, 1));
    }

    @Test
    void somethingThatIsNotAnOrderNumberIsRefused() {
        assertThatThrownBy(() -> Dependencies.orderFrom("nonsense")).isInstanceOf(BadReading.class);
    }

    @Test
    void aSerialParses() {
        assertThat(Dependencies.serialFrom("5127")).isEqualTo(new Serial("5127"));
    }

    @Test
    void aDayThatIsGivenIsTheOneUsed() {
        assertThat(Dependencies.dayOr(built(), Optional.of(LocalDate.of(2026, 1, 1))))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void aDayLeftOffTakesTheServicesOwn() {
        assertThat(Dependencies.dayOr(built(), Optional.empty())).isEqualTo(DAY);
    }

    @Test
    void aServiceWithNoPinnedDayFallsBackToToday() {
        assertThat(Dependencies.dayOr(built(null), Optional.empty())).isEqualTo(LocalDate.now());
    }

    /** The whole point of the split, asserted rather than trusted. */
    @Test
    void nothingHereImportsARoute() throws IOException {
        String written = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String javadoc = written.substring(written.indexOf("/**"), written.indexOf("public final class"));
        assertThat(javadoc).contains("route");
        List<String> imports = written.lines()
                .filter(line -> line.startsWith("import io.github.rkaushikdev.doublerbay"))
                .toList();
        assertThat(imports).isNotEmpty();
        assertThat(imports).noneMatch(line -> line.contains("doublerbay.api."));
        assertThat(written).doesNotContainPattern("[A-Za-z]+Routes\\b");
    }

    @Test
    void aTailOnTheRegisterComesBack() {
        Database database = Desk.newDatabase();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(Sample.flyingLog());
        });
        Serial held = UnitOfWork.apply(database, unit -> Dependencies.tailOn(unit, "5127"));
        assertThat(held).isEqualTo(new Serial("5127"));
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsRefused() {
        Database database = Desk.newDatabase();
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> Dependencies.tailOn(unit, "9999")))
                .isInstanceOf(NotOnFile.class);
    }

    /** The parse refuses first, which is a bad reading rather than a miss. */
    @Test
    void aSerialThatIsNotOneIsRefusedBeforeTheRegisterIsAsked() {
        Database database = Desk.newDatabase();
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> Dependencies.tailOn(unit, "not a serial")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a serial number");
    }
}
