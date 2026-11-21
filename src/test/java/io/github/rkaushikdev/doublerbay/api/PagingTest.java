package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PagingTest {

    private static final List<String> LETTERS = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    private static Built built() {
        Database database = Desk.stocked();
        return new Built(database, Api.clientFor(database));
    }

    private static void cards(Database database, int howMany) {
        UnitOfWork.run(database, unit -> {
            for (int step = 1; step <= howMany; step++) {
                unit.findings().raiseFinding(Sample.card(step));
            }
        });
    }

    @Test
    void aWindowDefaultsToTheFirstPage() {
        Window asked = Paging.window();
        assertThat(asked.offset()).isZero();
        assertThat(asked.limit()).isEqualTo(Paging.DEFAULT_LIMIT);
    }

    @Test
    void aWindowTakesWhatItIsGiven() {
        Window asked = Paging.window(10, 5);
        assertThat(asked.offset()).isEqualTo(10);
        assertThat(asked.stop()).isEqualTo(15);
    }

    @Test
    void aWindowReadsBackAsTheRowsItCovers() {
        assertThat(new Window(10, 5)).hasToString("rows 10 to 15");
    }

    @Test
    void anOffsetBeforeTheStartIsRefused() {
        assertThatThrownBy(() -> new Window(-1, 5))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not before it");
    }

    @Test
    void aPageOfNothingIsRefused() {
        assertThatThrownBy(() -> new Window(0, 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one row");
    }

    @Test
    void aPagePastTheCeilingIsRefused() {
        assertThatThrownBy(() -> new Window(0, Paging.HIGHEST_LIMIT + 1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at most " + Paging.HIGHEST_LIMIT);
    }

    @Test
    void aPageAtTheCeilingIsAllowed() {
        assertThat(new Window(0, Paging.HIGHEST_LIMIT).limit()).isEqualTo(Paging.HIGHEST_LIMIT);
    }

    @Test
    void aWindowCutsTheRowsItCovers() {
        assertThat(new Window(2, 3).cut(LETTERS)).containsExactly("c", "d", "e");
    }

    @Test
    void aWindowPastTheEndCutsNothing() {
        assertThat(new Window(50, 5).cut(LETTERS)).isEmpty();
    }

    @Test
    void aWindowOverTheEndCutsWhatIsLeft() {
        assertThat(new Window(8, 5).cut(LETTERS)).containsExactly("i", "j");
    }

    @Test
    void aPageCountsTheWholeListNotThePage() {
        Page<String> made = Paging.paged(LETTERS, new Window(0, 3), String::valueOf);
        assertThat(made.total()).isEqualTo(10);
        assertThat(made.rows()).hasSize(3);
    }

    @Test
    void aPageShapesEveryRow() {
        Page<Integer> made = Paging.paged(List.of(1, 2, 3), new Window(0, 3), one -> one * 2);
        assertThat(made.rows()).containsExactly(2, 4, 6);
    }

    @Test
    void aPageSaysThereIsMore() {
        Page<String> made = Paging.paged(LETTERS, new Window(0, 3), String::valueOf);
        assertThat(made.hasMore()).isTrue();
        assertThat(made.nextOffset()).contains(3);
    }

    @Test
    void theLastPageSaysThereIsNoMore() {
        Page<String> made = Paging.paged(LETTERS, new Window(8, 5), String::valueOf);
        assertThat(made.hasMore()).isFalse();
        assertThat(made.nextOffset()).isEmpty();
    }

    @Test
    void anEmptyListHasNoMore() {
        Page<String> made = Paging.paged(List.<String>of(), new Window(0, 5), String::valueOf);
        assertThat(made.hasMore()).isFalse();
        assertThat(made.total()).isZero();
    }

    @Test
    void theFindingsRoutePages() {
        Built built = built();
        cards(built.database(), 5);
        JsonNode body =
                built.api().get("/aircraft/5127/findings", Map.of("limit", 2)).json();
        assertThat(body.get("total").asInt()).isEqualTo(5);
        assertThat(body.get("rows")).hasSize(2);
        assertThat(body.get("limit").asInt()).isEqualTo(2);
    }

    @Test
    void theFindingsRouteTakesAnOffset() {
        Built built = built();
        cards(built.database(), 5);
        JsonNode body = built.api()
                .get("/aircraft/5127/findings", Map.of("offset", 4, "limit", 2))
                .json();
        assertThat(body.get("rows")).hasSize(1);
        assertThat(body.get("offset").asInt()).isEqualTo(4);
    }

    @Test
    void theFindingsRouteDefaultsToTheWholeShortList() {
        Built built = built();
        cards(built.database(), 5);
        JsonNode body = built.api().get("/aircraft/5127/findings").json();
        assertThat(body.get("rows")).hasSize(5);
    }

    @Test
    void aLimitOfNothingIsRefused() {
        Built built = built();
        Client.Answer answer = built.api().get("/aircraft/5127/findings", Map.of("limit", 0));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("at least one row");
    }

    @Test
    void aLimitPastTheCeilingIsRefused() {
        Built built = built();
        Client.Answer answer = built.api().get("/aircraft/5127/findings", Map.of("limit", Paging.HIGHEST_LIMIT + 1));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void anOffsetBeforeTheStartIsRefusedOverTheWire() {
        Built built = built();
        assertThat(built.api()
                        .get("/aircraft/5127/findings", Map.of("offset", -1))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/aircraft/9999/findings").status()).isEqualTo(404);
    }

    @Test
    void theDeferralListPages() {
        Built built = built();
        JsonNode body = built.api().get("/deferrals", Map.of("limit", 2)).json();
        assertThat(body.get("total").asInt()).isZero();
        assertThat(body.get("rows")).hasToString("[]");
    }

    @Test
    void theOrderListPages() {
        Built built = built();
        JsonNode body = built.api().get("/orders", Map.of("limit", 2)).json();
        assertThat(body.get("limit").asInt()).isEqualTo(2);
    }
}
