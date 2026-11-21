package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseTest {

    @Test
    void aStockedDatabaseHoldsTheTailItsCardAndItsDisposition() {
        Database made = Desk.stocked();
        FindingId card = Desk.assessedCard(made);
        UnitOfWork.run(made, unit -> {
            assertThat(unit.aircraft().get(new Serial("5127"))).isEqualTo(Sample.aircraft());
            assertThat(unit.aircraft().logFor(new Serial("5127"))).isEqualTo(Sample.flyingLog());
            assertThat(unit.findings().latest(card)).isEqualTo(Sample.card(1));
            assertThat(unit.dispositions().get(card).state()).isSameAs(DispositionState.ASSESSED);
        });
    }

    @Test
    void twoInMemoryDatabasesNeverShareTheirRows() {
        Database one = Desk.stocked();
        Database other = Desk.newDatabase();
        List<Aircraft> onOne = UnitOfWork.apply(one, unit -> unit.aircraft().all());
        List<Aircraft> onOther = UnitOfWork.apply(other, unit -> unit.aircraft().all());
        assertThat(onOne).hasSize(1);
        assertThat(onOther).isEmpty();
    }

    @Test
    void theListenerHearsTheStatementsUntilItIsTakenOff() {
        Database made = Desk.stocked();
        List<String> heard = made.statementsDuring(
                () -> UnitOfWork.run(made, unit -> unit.aircraft().all()));
        int before = heard.size();
        UnitOfWork.run(made, unit -> unit.aircraft().all());
        assertThat(before).isPositive();
        assertThat(heard).hasSize(before);
    }

    @Test
    void aClosedDatabaseRefusesWork() {
        Database made = Databases.fresh();
        made.close();
        made.close();
        assertThatThrownBy(made::factory).isInstanceOf(OutOfSequence.class);
    }

    @Test
    void theSchemaComesOffAndGoesBackOn() {
        Database made = Databases.fresh();
        made.dropSchema();
        made.buildSchema();
        List<Aircraft> after = UnitOfWork.apply(made, unit -> unit.aircraft().all());
        assertThat(after).isEmpty();
        assertThat(Database.tableNames()).hasSize(11).startsWith("aircraft").endsWith("inspection");
    }
}
