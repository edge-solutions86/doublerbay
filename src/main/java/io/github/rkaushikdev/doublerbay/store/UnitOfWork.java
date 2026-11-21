package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.OutOfSequence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One transaction, with every repository on it, and nothing committed by halves.
 *
 * <p>Raising a damage card writes the card and opens its disposition. If the first goes in and the
 * second does not, there is a finding on the register that no report will ever show because nothing
 * knows where it is. So the two go in together or neither does, and the boundary for that is here
 * rather than in whoever remembered to call commit.
 *
 * <p>Nothing commits on its own. The work is handed in as a function: the unit commits once when
 * it returns, and rolls the whole thing back if it throws. Every repository on one unit shares its
 * one session, and a unit that has finished refuses to do anything more.
 */
public final class UnitOfWork {

    private EntityManager session;

    private UnitOfWork(EntityManager session) {
        this.session = session;
    }

    /** Run some work in one transaction and hand back its answer. */
    public static <T> T apply(Database database, Function<UnitOfWork, T> work) {
        Objects.requireNonNull(work, "work");
        EntityManager opened = database.factory().createEntityManager();
        UnitOfWork unit = new UnitOfWork(opened);
        EntityTransaction transaction = opened.getTransaction();
        try {
            transaction.begin();
            T answer = work.apply(unit);
            unit.open().getTransaction().commit();
            return answer;
        } finally {
            EntityTransaction last = opened.getTransaction();
            if (last.isActive()) {
                last.rollback();
            }
            opened.close();
            unit.session = null;
        }
    }

    /** Run some work that answers nothing in one transaction. */
    public static void run(Database database, Consumer<UnitOfWork> work) {
        Objects.requireNonNull(work, "work");
        apply(database, unit -> {
            work.accept(unit);
            return null;
        });
    }

    /** The session underneath, for the few callers that need to reach the rows. */
    public EntityManager session() {
        return open();
    }

    /** Tails and their logs. */
    public AircraftRepository aircraft() {
        return new AircraftRepository(open());
    }

    /** Damage cards, every revision of every one. */
    public FindingRepository findings() {
        return new FindingRepository(open());
    }

    /** Where each card is, and how it got there. */
    public DispositionRepository dispositions() {
        return new DispositionRepository(open());
    }

    /** Deferrals, one live one per card. */
    public DeferralRepository deferrals() {
        return new DeferralRepository(open());
    }

    /** Repairs, by number, tail or panel. */
    public RepairRepository repairs() {
        return new RepairRepository(open());
    }

    /** The corrosion programme's own register. */
    public CorrosionRepository corrosion() {
        return new CorrosionRepository(open());
    }

    /** Questions out to the manufacturer. */
    public OrderRepository orders() {
        return new OrderRepository(open());
    }

    /** The repeat inspections the fitted repairs carry. */
    public InspectionRepository inspections() {
        return new InspectionRepository(open());
    }

    /** Push the writes down so a later read in the same unit sees them. */
    public void flush() {
        open().flush();
    }

    /**
     * Undo everything written so far in this unit. The unit stays open, and anything written after
     * this is committed when the work returns.
     */
    public void rollback() {
        EntityManager held = open();
        held.getTransaction().rollback();
        held.clear();
        held.getTransaction().begin();
    }

    private EntityManager open() {
        if (session == null) {
            throw new OutOfSequence("this unit of work is not open, use it inside its work");
        }
        return session;
    }
}
