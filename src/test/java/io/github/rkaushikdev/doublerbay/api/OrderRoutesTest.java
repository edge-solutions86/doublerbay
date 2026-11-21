package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

class OrderRoutesTest {

    private static final String DAY = "2025-03-14";
    private static final String SENT = "2025-03-20";
    private static final String ANSWERED = "2025-06-01";
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    private static Built built() {
        Database database = Desk.stocked();
        return new Built(database, Api.clientFor(database));
    }

    private static String assessed(Database database) {
        return Desk.assessedCard(database, 1).toString();
    }

    private static Client.Answer raiseOne(Client api, String against) {
        return raiseOne(api, against, QUESTION);
    }

    private static Client.Answer raiseOne(Client api, String against, String question) {
        return api.post("/orders", json("against", against, "question", question, "raised", DAY, "by", "a.okonkwo"));
    }

    private static Map<String, Object> approval() {
        return approval("permanent with a repeat inspection");
    }

    private static Map<String, Object> approval(String category) {
        return json(
                "on",
                ANSWERED,
                "by",
                "a.okonkwo",
                "sheet",
                "RAS-2025-4417",
                "category",
                category,
                "conditions",
                List.of("inspect at every C check"),
                "valid_until",
                "2027-01-01");
    }

    private static DispositionState stateOf(Database database, FindingId reference) {
        return UnitOfWork.apply(
                database, unit -> unit.dispositions().get(reference).state());
    }

    private static void sent(Client api) {
        api.post("/orders/EO-2025-0001/sent", json("on", SENT));
    }

    @Test
    void anOrderCanBeRaised() {
        Built built = built();
        Client.Answer answer = raiseOne(built.api(), assessed(built.database()));
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        assertThat(answer.json().get("reference").asText()).isEqualTo("EO-2025-0001");
        assertThat(answer.json().get("open").asBoolean()).isTrue();
    }

    @Test
    void raisingHoldsTheCard() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        assertThat(stateOf(built.database(), new FindingId(2025, 1))).isSameAs(DispositionState.HELD);
    }

    @Test
    void aQuestionThatSaysNothingIsRefused() {
        Built built = built();
        Client.Answer answer = raiseOne(built.api(), assessed(built.database()), "help");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("says what is being asked");
    }

    @Test
    void aCardThatIsNotOnFileCannotGoOut() {
        Built built = built();
        assertThat(raiseOne(built.api(), "DR-2025-000099").status()).isEqualTo(404);
    }

    @Test
    void aCardAlreadyOutCannotGoOutAgain() {
        Built built = built();
        String reference = assessed(built.database());
        raiseOne(built.api(), reference);
        assertThat(raiseOne(built.api(), reference).status()).isEqualTo(409);
    }

    @Test
    void aMechanicMayNotRaiseAnOrder() {
        Built built = built();
        Client.Answer answer = built.api()
                .post(
                        "/orders",
                        json(
                                "against",
                                assessed(built.database()),
                                "question",
                                QUESTION,
                                "raised",
                                DAY,
                                "by",
                                "a.okonkwo"),
                        Desk.headersFor("mechanic"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void theOpenListCarriesWhatIsOut() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        assertThat(built.api().get("/orders").json().get("total").asInt()).isEqualTo(1);
    }

    @Test
    void theOpenListIsEmptyBeforeAnythingGoesOut() {
        Built built = built();
        assertThat(built.api().get("/orders").json().get("rows")).hasToString("[]");
    }

    @Test
    void oneOrderCanBeReadBack() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        assertThat(built.api()
                        .get("/orders/EO-2025-0001")
                        .json()
                        .get("question")
                        .asText())
                .isEqualTo(QUESTION);
    }

    @Test
    void anOrderThatIsNotOnFileIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/orders/EO-2025-0099").status()).isEqualTo(404);
    }

    @Test
    void aReferenceThatIsNotOneIsRefused() {
        Built built = built();
        assertThat(built.api().get("/orders/nonsense").status()).isEqualTo(422);
    }

    @Test
    void sendingRecordsTheDay() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        JsonNode body =
                built.api().post("/orders/EO-2025-0001/sent", json("on", SENT)).json();
        assertThat(body.get("state").asText()).isEqualTo("sent");
        assertThat(body.get("sent").asText()).isEqualTo(SENT);
    }

    @Test
    void sendingOneThatHasAlreadyGoneIsRefused() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        assertThat(built.api()
                        .post("/orders/EO-2025-0001/sent", json("on", SENT))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void acknowledgingMovesItOn() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        assertThat(built.api()
                        .post("/orders/EO-2025-0001/acknowledged")
                        .json()
                        .get("state")
                        .asText())
                .isEqualTo("acknowledged");
    }

    @Test
    void anApprovalComesBackWithItsConditions() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        JsonNode body =
                built.api().post("/orders/EO-2025-0001/approved", approval()).json();
        assertThat(body.get("approval").get("sheet").asText()).isEqualTo("RAS-2025-4417");
        assertThat(body.get("approval").get("conditions")).hasToString("[\"inspect at every C check\"]");
        assertThat(body.get("open").asBoolean()).isFalse();
    }

    @Test
    void anApprovalReleasesTheCard() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        built.api().post("/orders/EO-2025-0001/approved", approval());
        assertThat(stateOf(built.database(), new FindingId(2025, 1))).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void aCategoryThatIsNotOneIsRefused() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        Client.Answer answer = built.api().post("/orders/EO-2025-0001/approved", approval("whatever"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("not a repair category");
    }

    @Test
    void aSheetNumberThatIsNotOneIsRefused() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        Map<String, Object> sent = approval();
        sent.put("sheet", "4417");
        assertThat(built.api().post("/orders/EO-2025-0001/approved", sent).status())
                .isEqualTo(422);
    }

    @Test
    void aRejectionReleasesTheCard() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        Client.Answer answer =
                built.api().post("/orders/EO-2025-0001/rejected", json("on", ANSWERED, "by", "a.okonkwo"));
        assertThat(answer.json().get("state").asText()).isEqualTo("rejected");
        assertThat(stateOf(built.database(), new FindingId(2025, 1))).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void aQuestionCanBeWithdrawn() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        Client.Answer answer =
                built.api().post("/orders/EO-2025-0001/withdrawn", json("on", ANSWERED, "by", "a.okonkwo"));
        assertThat(answer.json().get("state").asText()).isEqualTo("withdrawn");
    }

    @Test
    void nothingNeedsChasingTheDayItGoesOut() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        assertThat(built.api().get("/orders/chase", Map.of("on", SENT)).json()).hasToString("[]");
    }

    @Test
    void anOrderOutALongTimeNeedsChasing() {
        Built built = built();
        raiseOne(built.api(), assessed(built.database()));
        sent(built.api());
        JsonNode body =
                built.api().get("/orders/chase", Map.of("on", "2026-01-01")).json();
        assertThat(body.get(0).get("order").get("reference").asText()).isEqualTo("EO-2025-0001");
        assertThat(body.get(0).get("days_waiting").asInt()).isGreaterThan(200);
    }

    @Test
    void theChaseListNeedsADay() {
        Built built = built();
        assertThat(built.api().get("/orders/chase").status()).isEqualTo(422);
    }
}
