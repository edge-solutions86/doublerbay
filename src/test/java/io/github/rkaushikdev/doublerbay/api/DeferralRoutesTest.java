package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeferralRoutesTest {

    /** A database and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    private static Built built() {
        Database database = Desk.newDatabase();
        return new Built(database, Api.clientFor(database));
    }

    /** One tail enrolled over the wire, with a reading posted against it. */
    private static Built stocked() {
        Built built = built();
        built.api()
                .post(
                        "/aircraft",
                        json(
                                "serial", "5127",
                                "registration", "N412VD",
                                "series", "200",
                                "line_number", 1140,
                                "delivered", "2016-04-12"));
        built.api()
                .post(
                        "/aircraft/5127/readings",
                        json("taken", "2025-03-01", "usage", json("cycles", 13800, "hours", "24200.0")));
        return built;
    }

    /** One card, raised and looked up, off the shared helper. */
    private static String assessed(Database database) {
        return Desk.assessedCard(database).toString();
    }

    private static Map<String, Object> body(String against, Object... rest) {
        Map<String, Object> sent = json("against", against, "granted", "2025-03-14", "by", "j.mercer");
        sent.putAll(json(rest));
        return sent;
    }

    @Test
    void aCardCanBeDeferred() {
        Built built = stocked();
        Client.Answer answer = built.api().post("/deferrals", body(assessed(built.database())));
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        assertThat(answer.json().get("against").asText()).isEqualTo("DR-2025-000001");
    }

    @Test
    void theDeferralComesBackWithWhatItIsDueAgainst() {
        Built built = stocked();
        Client.Answer answer = built.api().post("/deferrals", body(assessed(built.database()), "limit", "500 FC"));
        JsonNode due = answer.json().get("due");
        assertThat(due.get("cycles").asInt()).isEqualTo(14300);
        assertThat(due.get("on").isNull()).isTrue();
        assertThat(due.get("written").asText()).contains("14300 FC");
    }

    @Test
    void aDeferralMovesTheDispositionToo() {
        Built built = stocked();
        built.api().post("/deferrals", body(assessed(built.database())));
        DispositionState state = UnitOfWork.apply(
                built.database(),
                unit -> unit.dispositions().get(new FindingId(2025, 1)).state());
        assertThat(state).isSameAs(DispositionState.DEFERRED);
    }

    @Test
    void aCardThatHasNotBeenAssessedCannotBeDeferred() {
        Built built = stocked();
        Desk.raiseCard(built.database());
        Client.Answer answer = built.api().post("/deferrals", body("DR-2025-000001"));
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("detail").asText()).contains("cannot go from raised to deferred");
    }

    @Test
    void aRefusedDeferralWritesNothing() {
        Built built = stocked();
        Desk.raiseCard(built.database());
        built.api().post("/deferrals", body("DR-2025-000001"));
        List<Deferral> live =
                UnitOfWork.apply(built.database(), unit -> unit.deferrals().live());
        assertThat(live).isEmpty();
    }

    @Test
    void aLimitArrivesAsTheTextTheManualPrints() {
        Built built = stocked();
        Client.Answer answer =
                built.api().post("/deferrals", body(assessed(built.database()), "limit", "300 FC / 90 DY"));
        assertThat(answer.json().get("limit").asText()).isEqualTo("300 FC / 90 DY");
    }

    @Test
    void aLimitThatCannotBeReadIsRefused() {
        Built built = stocked();
        Client.Answer answer = built.api().post("/deferrals", body(assessed(built.database()), "limit", "soon"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aDeferralToACheckIsCutAtTheCheck() {
        Built built = stocked();
        Client.Answer answer =
                built.api().post("/deferrals", body(assessed(built.database()), "next_check", "2025-04-01"));
        assertThat(answer.json().get("due").get("on").asText()).isEqualTo("2025-04-01");
    }

    @Test
    void theMethodCrossesTheWire() {
        Built built = stocked();
        Client.Answer answer = built.api()
                .post("/deferrals", body(assessed(built.database()), "method", "high frequency eddy current"));
        assertThat(answer.json().get("re_inspect_with").asText()).isEqualTo("high frequency eddy current");
    }

    @Test
    void aMethodThatIsNotOneIsRefused() {
        Built built = stocked();
        Client.Answer answer =
                built.api().post("/deferrals", body(assessed(built.database()), "method", "a good hard look"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aMechanicMayNotDefer() {
        Built built = stocked();
        String reference = assessed(built.database());
        Client.Answer answer = built.api().post("/deferrals", body(reference), Desk.headersFor("mechanic"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("may not grant a deferral");
    }

    @Test
    void aRequestWithNoKeyIsRefused() {
        Built built = stocked();
        Client.Answer answer =
                built.api().post("/deferrals", body(assessed(built.database())), Map.of(Desk.KEY_HEADER, ""));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aCardThatIsNotOnFileCannotBeDeferred() {
        Built built = stocked();
        assertThat(built.api().post("/deferrals", body("DR-2025-000099")).status())
                .isEqualTo(404);
    }

    @Test
    void deferringACardTwiceIsRefused() {
        Built built = stocked();
        String reference = assessed(built.database());
        built.api().post("/deferrals", body(reference));
        assertThat(built.api().post("/deferrals", body(reference)).status()).isEqualTo(422);
    }

    @Test
    void theLiveListCarriesWhatIsDeferred() {
        Built built = stocked();
        built.api().post("/deferrals", body(assessed(built.database())));
        assertThat(built.api().get("/deferrals").json().get("total").asInt()).isEqualTo(1);
    }

    @Test
    void theLiveListIsEmptyBeforeAnythingIsDeferred() {
        Built built = stocked();
        assertThat(built.api().get("/deferrals").json().get("rows")).isEmpty();
    }

    @Test
    void oneDeferralCanBeReadBack() {
        Built built = stocked();
        String reference = assessed(built.database());
        built.api().post("/deferrals", body(reference));
        assertThat(built.api().get("/deferrals/" + reference).json().get("by").asText())
                .isEqualTo("j.mercer");
    }

    @Test
    void aCardThatIsNotDeferredComesBackAsNotFound() {
        Built built = stocked();
        assertThat(built.api().get("/deferrals/DR-2025-000099").status()).isEqualTo(404);
    }

    @Test
    void aDeferralCanBeTightened() {
        Built built = stocked();
        String reference = assessed(built.database());
        built.api().post("/deferrals", body(reference, "limit", "500 FC"));
        Client.Answer answer =
                built.api().post("/deferrals/" + reference + "/tighten", json("limit", "50 FC", "by", "a.okonkwo"));
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("limit").asText()).isEqualTo("50 FC");
        assertThat(answer.json().get("by").asText()).isEqualTo("a.okonkwo");
    }

    @Test
    void tighteningKeepsTheDayItWasGranted() {
        Built built = stocked();
        String reference = assessed(built.database());
        built.api().post("/deferrals", body(reference, "limit", "500 FC"));
        Client.Answer answer =
                built.api().post("/deferrals/" + reference + "/tighten", json("limit", "50 FC", "by", "a.okonkwo"));
        assertThat(answer.json().get("granted").asText()).isEqualTo("2025-03-14");
    }

    @Test
    void aDeferralCanBeLifted() {
        Built built = stocked();
        String reference = assessed(built.database());
        built.api().post("/deferrals", body(reference));
        assertThat(built.api()
                        .post("/deferrals/" + reference + "/lift", json("on", "2025-06-01"))
                        .status())
                .isEqualTo(204);
        assertThat(built.api().get("/deferrals").json().get("rows")).isEmpty();
    }

    @Test
    void liftingOneThatWasNeverGrantedIsRefused() {
        Built built = stocked();
        Client.Answer answer = built.api().post("/deferrals/DR-2025-000099/lift", json("on", "2025-06-01"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aReaderMayNotLiftADeferral() {
        Built built = stocked();
        String reference = assessed(built.database());
        built.api().post("/deferrals", body(reference));
        Client.Answer answer = built.api()
                .post("/deferrals/" + reference + "/lift", json("on", "2025-06-01"), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(422);
    }
}
