package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.CorrosionRow;
import io.github.rkaushikdev.doublerbay.store.DeferralRow;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import java.time.LocalDate;

/**
 * The three questions taking a card in and measuring one again ask of the programme's and the
 * deferrals' registers, answered off their rows in the same unit of work.
 */
final class Registers {

    private Registers() {}

    /**
     * Whether this panel on this aeroplane was graded before this day, which is what makes a level
     * two. Entries raised later never count, or a later inspection would reach back and turn an old
     * level one into a level two.
     */
    static boolean corrosionFoundHereBefore(UnitOfWork unit, Serial serial, PanelId part, LocalDate before) {
        Long seen = unit.session()
                .createQuery(
                        "select count(c) from CorrosionRow c"
                                + " where c.serial = :serial and c.part = :part and c.foundOn < :before",
                        Long.class)
                .setParameter("serial", serial.number())
                .setParameter("part", part)
                .setParameter("before", before)
                .getSingleResult();
        return seen > 0;
    }

    /** File a graded finding against the tail and the card it came off, not yet reported. */
    static void recordCorrosion(UnitOfWork unit, CorrosionEntry entry, Serial serial, FindingId against) {
        unit.session()
                .persist(new CorrosionRow()
                        .setSerial(serial)
                        .setAgainst(against)
                        .setPart(entry.part())
                        .setLevel(entry.level().value())
                        .setFoundOn(entry.foundOn())
                        .setReportedOn(null));
    }

    /** Whether a card is flying against a deferral that has not been lifted. */
    static boolean isDeferred(UnitOfWork unit, FindingId reference) {
        DeferralRow row = unit.session().find(DeferralRow.class, reference.toString());
        return row != null && row.getLifted().isEmpty();
    }
}
