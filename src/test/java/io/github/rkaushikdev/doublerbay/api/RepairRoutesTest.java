package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepairRoutesTest {

    private static final String FITTED = "2025-04-02";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    /** One card on a fastener row, raised and looked up. */
    private static String assessed(Database database) {
        return assessed(database, 1);
    }

    private static String assessed(Database database, int sequence) {
        return Desk.assessedCard(database, Sample.card(sequence, Sample.cabinPointOn(10)))
                .toString();
    }

    private static Map<String, Object> body(String against, Object... rest) {
        return sent(against, 1, "468.000", "91.000", rest);
    }

    /** The default body under another repair number. */
    private static Map<String, Object> numbered(String against, int sequence) {
        return sent(against, sequence, "468.000", "91.000");
    }

    /** The default body under another repair number, centred somewhere else. */
    private static Map<String, Object> placed(String against, int sequence, String centreAlong, String centreAround) {
        return sent(against, sequence, centreAlong, centreAround);
    }

    private static Map<String, Object> sent(
            String against, int sequence, String centreAlong, String centreAround, Object... rest) {
        Map<String, Object> sent = json(
                "against", against,
                "sequence", sequence,
                "along", "2.000",
                "around", "2.000",
                "centre_along", centreAlong,
                "centre_around", centreAround,
                "embodied", FITTED,
                "by", "s.brandt");
        sent.putAll(json(rest));
        return sent;
    }

    @Test
    void aRepairIsDrawnAndFitted() {
        Built built = built();
        Client.Answer answer = built.api().post("/repairs", body(assessed(built.database())));
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        assertThat(answer.json().get("repair").get("reference").asText()).isEqualTo("RP-53-0001");
    }

    @Test
    void theDrawingComesBackWithTheServiceSOwnNumbers() {
        Built built = built();
        JsonNode drawn = built.api()
                .post("/repairs", body(assessed(built.database())))
                .json()
                .get("repair")
                .get("doubler");
        assertThat(drawn.get("fastener_count").asInt())
                .isEqualTo(drawn.get("rows_each_side").asInt()
                        * drawn.get("per_row").asInt()
                        * 4);
        assertThat(drawn.get("material").asText()).startsWith("2024");
    }

    @Test
    void fittingClosesTheCard() {
        Built built = built();
        Client.Answer answer = built.api().post("/repairs", body(assessed(built.database())));
        assertThat(answer.json().get("card_state").asText()).isEqualTo("closed");
    }

    @Test
    void aTemporaryRepairLeavesTheCardInWork() {
        Built built = built();
        Client.Answer answer = built.api().post("/repairs", body(assessed(built.database()), "temporary", true));
        assertThat(answer.json().get("card_state").asText()).isEqualTo("in work");
        assertThat(answer.json().get("repair").get("category").asText()).isEqualTo("temporary, to be replaced");
    }

    @Test
    void aFastenerSizeCanBeAskedFor() {
        Built built = built();
        Client.Answer answer = built.api().post("/repairs", body(assessed(built.database()), "fastener_dash", 6));
        assertThat(answer.json()
                        .get("repair")
                        .get("doubler")
                        .get("fastener_dash")
                        .asInt())
                .isEqualTo(6);
    }

    @Test
    void aCleanFitReportsNoClashes() {
        Built built = built();
        assertThat(built.api()
                        .post("/repairs", body(assessed(built.database())))
                        .json()
                        .get("clashes"))
                .hasToString("[]");
    }

    @Test
    void aSecondRepairTooCloseIsReportedAsAClash() {
        Built built = built();
        built.api().post("/repairs", placed(assessed(built.database(), 1), 1, "468.000", "91.000"));
        Client.Answer answer =
                built.api().post("/repairs", placed(assessed(built.database(), 2), 2, "469.000", "91.000"));
        assertThat(answer.json().get("clashes")).hasToString("[[\"RP-53-0001\",\"RP-53-0002\"]]");
    }

    @Test
    void aRepairAgainstACardThatIsNotOnFileIsNotFound() {
        Built built = built();
        assertThat(built.api().post("/repairs", body("DR-2025-000099")).status())
                .isEqualTo(404);
    }

    @Test
    void aRepairNumberUsedTwiceIsRefused() {
        Built built = built();
        built.api().post("/repairs", numbered(assessed(built.database(), 1), 1));
        Client.Answer answer = built.api().post("/repairs", numbered(assessed(built.database(), 2), 1));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aLengthThatIsNotALengthIsRefused() {
        Built built = built();
        Client.Answer answer = built.api().post("/repairs", body(assessed(built.database()), "along", "big"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("not a repair length");
    }

    @Test
    void aPageThatIsNotAReferenceIsRefused() {
        Built built = built();
        Client.Answer answer = built.api().post("/repairs", body(assessed(built.database()), "page", "page four"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aMechanicMayNotFitARepair() {
        Built built = built();
        Client.Answer answer =
                built.api().post("/repairs", body(assessed(built.database())), Desk.headersFor("mechanic"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void theRepairsOnATailComeBack() {
        Built built = built();
        built.api().post("/repairs", body(assessed(built.database())));
        assertThat(built.api().get("/repairs/aircraft/5127").json().get("total").asInt())
                .isEqualTo(1);
    }

    @Test
    void aTailWithNoRepairsComesBackEmpty() {
        Built built = built();
        assertThat(built.api().get("/repairs/aircraft/5127").json().get("rows")).hasToString("[]");
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/repairs/aircraft/9999").status()).isEqualTo(404);
    }

    @Test
    void oneRepairCanBeReadBack() {
        Built built = built();
        built.api().post("/repairs", body(assessed(built.database())));
        JsonNode bodyBack = built.api().get("/repairs/53/1").json();
        assertThat(bodyBack.get("against").asText()).isEqualTo("DR-2025-000001");
        assertThat(bodyBack.get("removed").isNull()).isTrue();
    }

    @Test
    void aRepairThatIsNotOnFileIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/repairs/53/99").status()).isEqualTo(404);
    }

    @Test
    void aRepairCanBeTakenOff() {
        Built built = built();
        built.api().post("/repairs", body(assessed(built.database())));
        assertThat(built.api()
                        .post("/repairs/53/1/remove", json("on", "2026-01-05"))
                        .status())
                .isEqualTo(204);
        assertThat(built.api().get("/repairs/aircraft/5127").json().get("rows")).hasToString("[]");
    }

    @Test
    void aRemovedRepairIsStillOnTheHistory() {
        Built built = built();
        built.api().post("/repairs", body(assessed(built.database())));
        built.api().post("/repairs/53/1/remove", json("on", "2026-01-05"));
        JsonNode every = built.api()
                .get("/repairs/aircraft/5127", Map.of("fitted_only", false))
                .json()
                .get("rows");
        assertThat(every.get(0).get("removed").asText()).isEqualTo("2026-01-05");
    }

    @Test
    void removingOneThatIsNotOnFileIsNotFound() {
        Built built = built();
        assertThat(built.api()
                        .post("/repairs/53/99/remove", json("on", "2026-01-05"))
                        .status())
                .isEqualTo(404);
    }

    @Test
    void aRepairCannotComeOffBeforeItWentOn() {
        Built built = built();
        built.api().post("/repairs", body(assessed(built.database())));
        assertThat(built.api()
                        .post("/repairs/53/1/remove", json("on", "2024-01-01"))
                        .status())
                .isEqualTo(422);
    }
}
