package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityTest {

    private static Client client() {
        return client(null);
    }

    private static Client client(String key) {
        return Client.over(Api.app(Api.depsFor(Desk.stocked()))).as(key);
    }

    private static Map<String, Object> cardBody() {
        return json(
                "serial", "5127",
                "kind", "dent",
                "origin", "impact",
                "source", "scheduled check",
                "found_on", "2025-03-14",
                "at_usage", json("cycles", 13900, "hours", "24400.0"),
                "fuselage",
                        json(
                                "structure", "fuselage",
                                "frame", 15,
                                "aft_of_frame", "10.000",
                                "stringer", 10,
                                "below_stringer", "4.000",
                                "side", "L"),
                "measurements",
                        json(
                                "depth", "0.010",
                                "along", "6.000",
                                "around", "4.000",
                                "sharp_bottom", false,
                                "cracked", false));
    }

    @Test
    void aReaderMayOnlyRead() {
        assertThat(Role.READER.can(Action.READ)).isTrue();
        for (Action action : List.of(Action.RAISE_CARD, Action.DISPOSITION, Action.AMEND_FLEET)) {
            assertThat(Role.READER.can(action)).isFalse();
        }
    }

    @Test
    void aMechanicRaisesCardsAndPostsReadingsAndNothingElse() {
        assertThat(Role.MECHANIC.can(Action.RAISE_CARD)).isTrue();
        assertThat(Role.MECHANIC.can(Action.POST_READING)).isTrue();
        assertThat(Role.MECHANIC.can(Action.DISPOSITION)).isFalse();
        assertThat(Role.MECHANIC.can(Action.DEFER)).isFalse();
    }

    @Test
    void anEngineerDispositionsButDoesNotGrantTheLongDeferrals() {
        assertThat(Role.ENGINEER.can(Action.DISPOSITION)).isTrue();
        assertThat(Role.ENGINEER.can(Action.DEFER)).isFalse();
        assertThat(Role.ENGINEER.can(Action.AMEND_FLEET)).isFalse();
    }

    @Test
    void theChiefMayDoEverything() {
        for (Action action : Action.values()) {
            assertThat(Role.CHIEF.can(action)).isTrue();
        }
    }

    @Test
    void aKeyBelongsToSomebodyAndIsLongEnough() {
        assertThatThrownBy(() -> new ApiKey("short", "somebody", Role.READER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least eight characters");
        assertThatThrownBy(() -> new ApiKey("longenoughkey", "  ", Role.READER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("belongs to somebody");
    }

    @Test
    void aKeyReadsBackWhoIsBehindIt() {
        assertThat(new ApiKey("longenoughkey", "the duty engineer", Role.ENGINEER))
                .hasToString("the duty engineer (engineer)");
    }

    @Test
    void aRingWillNotIssueTheSameKeyTwice() {
        KeyRing ring = new KeyRing();
        ring.issue(new ApiKey("longenoughkey", "somebody", Role.READER));
        assertThatThrownBy(() -> ring.issue(new ApiKey("longenoughkey", "somebody else", Role.CHIEF)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already been issued");
    }

    @Test
    void aRevokedKeyStopsWorking() {
        KeyRing ring = Security.openRing();
        int before = ring.size();
        ring.revoke("reader-key-0001");
        assertThat(ring.size()).isEqualTo(before - 1);
        assertThatThrownBy(() -> ring.holderOf("reader-key-0001"))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not one this service accepts");
    }

    @Test
    void revokingAKeyThatWasNeverIssuedIsRefused() {
        assertThatThrownBy(() -> new KeyRing().revoke("nothing"))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("never issued");
    }

    @Test
    void aRequestWithNoKeyIsRefused() {
        assertThatThrownBy(() -> Security.check(Security.openRing(), null, Action.READ))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("every request says who it is from");
    }

    @Test
    void aRequestWithTheWrongRoleIsRefusedWithTheReason() {
        assertThatThrownBy(() -> Security.check(Security.openRing(), "reader-key-0001", Action.RAISE_CARD))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("a reader may not raise a damage card");
    }

    @Test
    void theDevelopmentRingCarriesOneKeyOfEachRole() {
        assertThat(Security.openRing().size()).isEqualTo(Role.values().length);
    }

    @Test
    void raisingACardWithNoKeyComesBackRefused() {
        Client api = client();
        Client.Answer answer = api.post("/findings", cardBody());
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("says who it is from");
    }

    @Test
    void raisingACardAsAReaderComesBackRefused() {
        Client api = client("reader-key-0001");
        Client.Answer answer = api.post("/findings", cardBody());
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("may not raise a damage card");
    }

    @Test
    void raisingACardAsAMechanicGoesThrough() {
        Client api = client("mechanic-key-0001");
        assertThat(api.post("/findings", cardBody()).status()).isEqualTo(201);
    }

    @Test
    void aMechanicCannotSupersedeACard() {
        Client api = client("mechanic-key-0001");
        String first = api.post("/findings", cardBody()).json().get("reference").asText();
        String second =
                api.post("/findings", cardBody()).json().get("reference").asText();
        Client.Answer answer = api.post("/findings/" + first + "/supersede", json("by", second));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("may not disposition");
    }

    @Test
    void anEngineerCanSupersedeACard() {
        Client api = client("engineer-key-0001");
        String first = api.post("/findings", cardBody()).json().get("reference").asText();
        String second =
                api.post("/findings", cardBody()).json().get("reference").asText();
        assertThat(api.post("/findings/" + first + "/supersede", json("by", second))
                        .status())
                .isEqualTo(200);
    }

    @Test
    void onlyTheChiefAmendsTheRegister() {
        Map<String, Object> body = json(
                "serial", "5240",
                "registration", "N908QA",
                "series", "200",
                "line_number", 1200,
                "delivered", "2018-09-03");
        assertThat(client("engineer-key-0001").post("/aircraft", body).status()).isEqualTo(422);
        assertThat(client("chief-key-0001").post("/aircraft", body).status()).isEqualTo(201);
    }

    @Test
    void readingNeedsNoKeyAtAll() {
        Client api = client();
        assertThat(api.get("/aircraft/5127").status()).isEqualTo(200);
        assertThat(api.get("/health").status()).isEqualTo(200);
    }

    @Test
    void aKeyNobodyIssuedIsRefused() {
        Client api = client("not-a-real-key");
        Client.Answer answer = api.post("/findings", cardBody());
        assertThat(answer.status()).isEqualTo(404);
    }
}
