package io.github.rkaushikdev.doublerbay.support.probe;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.store.CyclesColumn;
import io.github.rkaushikdev.doublerbay.store.FindingIdColumn;
import io.github.rkaushikdev.doublerbay.store.HoursColumn;
import io.github.rkaushikdev.doublerbay.store.MilsColumn;
import io.github.rkaushikdev.doublerbay.store.PanelIdColumn;
import io.github.rkaushikdev.doublerbay.store.RegistrationColumn;
import io.github.rkaushikdev.doublerbay.store.RepairIdColumn;
import io.github.rkaushikdev.doublerbay.store.SerialColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A table with one column of every stored type on it, every one of them allowed to be empty, for
 * testing the column types without the rest of the schema. It lives outside the store package so the
 * real persistence unit never sees it.
 */
@Entity
@Table(name = "probe")
public class ProbeRow {

    @Id
    @Column(name = "id")
    private Integer id;

    @Convert(converter = MilsColumn.class)
    @Column(name = "depth")
    private Length depth;

    @Convert(converter = CyclesColumn.class)
    @Column(name = "cycles")
    private Cycles cycles;

    @Convert(converter = HoursColumn.class)
    @Column(name = "hours")
    private Hours hours;

    @Convert(converter = SerialColumn.class)
    @Column(name = "serial", length = 8)
    private Serial serial;

    @Convert(converter = RegistrationColumn.class)
    @Column(name = "marks", length = 8)
    private Registration marks;

    @Convert(converter = FindingIdColumn.class)
    @Column(name = "finding", length = 16)
    private FindingId finding;

    @Convert(converter = RepairIdColumn.class)
    @Column(name = "repair", length = 16)
    private RepairId repair;

    @Convert(converter = PanelIdColumn.class)
    @Column(name = "panel", length = 16)
    private PanelId panel;

    @Column(name = "note", length = 16)
    private String note;

    /** An empty row, for the persistence provider. */
    public ProbeRow() {}

    /** A row with nothing in it but its key. */
    public ProbeRow(int id) {
        this.id = id;
    }

    public Length getDepth() {
        return depth;
    }

    public ProbeRow setDepth(Length value) {
        this.depth = value;
        return this;
    }

    public Cycles getCycles() {
        return cycles;
    }

    public ProbeRow setCycles(Cycles value) {
        this.cycles = value;
        return this;
    }

    public Hours getHours() {
        return hours;
    }

    public ProbeRow setHours(Hours value) {
        this.hours = value;
        return this;
    }

    public Serial getSerial() {
        return serial;
    }

    public ProbeRow setSerial(Serial value) {
        this.serial = value;
        return this;
    }

    public Registration getMarks() {
        return marks;
    }

    public ProbeRow setMarks(Registration value) {
        this.marks = value;
        return this;
    }

    public FindingId getFinding() {
        return finding;
    }

    public ProbeRow setFinding(FindingId value) {
        this.finding = value;
        return this;
    }

    public RepairId getRepair() {
        return repair;
    }

    public ProbeRow setRepair(RepairId value) {
        this.repair = value;
        return this;
    }

    public PanelId getPanel() {
        return panel;
    }

    public ProbeRow setPanel(PanelId value) {
        this.panel = value;
        return this;
    }

    public String getNote() {
        return note;
    }

    public ProbeRow setNote(String value) {
        this.note = value;
        return this;
    }
}
