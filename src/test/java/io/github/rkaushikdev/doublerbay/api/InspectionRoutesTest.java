package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionRoutesTest {

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    @Test
    void aTailWithNoRepairsCarriesNoTasks() {
        Built built = built();
        assertThat(built.api().get("/inspections/aircraft/5127").json()).hasToString("[]");
    }

    @Test
    void aRepairCarryingNoTaskIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/inspections/53/1").status()).isEqualTo(404);
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/inspections/aircraft/9999").status()).isEqualTo(404);
    }

    @Test
    void theDueRouteNeedsADay() {
        Built built = built();
        assertThat(built.api().get("/inspections/aircraft/5127/due").status()).isEqualTo(422);
    }

    @Test
    void signingOffATaskThatIsNotThereIsNotFound() {
        Built built = built();
        Client.Answer answer = built.api()
                .post(
                        "/inspections/53/9/done",
                        json("on", "2026-01-05", "flown", json("cycles", 15000, "hours", "26000.0")));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aReaderMayLookAtTheTasks() {
        Built built = built();
        Client.Answer answer = built.api().get("/inspections/aircraft/5127", Map.of(), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }
}
