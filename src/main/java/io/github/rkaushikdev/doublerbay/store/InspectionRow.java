package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The repeat inspection one repair carries, and when it was last done. A repair carries at most one,
 * so the repair number is the whole key.
 */
@Entity
@Table(name = "inspection", indexes = @Index(name = "ix_inspection_last_done", columnList = "last_done"))
public class InspectionRow extends Recorded {

    @Id
    @Column(name = "against", length = 16, nullable = false)
    private String against;

    @Column(name = "part", length = 16, nullable = false)
    private PanelId part;

    @Column(name = "method", length = 32, nullable = false)
    private String method;

    @Column(name = "threshold_cycles")
    private Cycles thresholdCycles;

    @Column(name = "threshold_months")
    private Integer thresholdMonths;

    @Column(name = "repeat_cycles")
    private Cycles repeatCycles;

    @Column(name = "embodied", nullable = false)
    private LocalDate embodied;

    @Column(name = "at_cycles", nullable = false)
    private Cycles atCycles;

    @Column(name = "at_hours", nullable = false)
    private Hours atHours;

    @Column(name = "last_done")
    private LocalDate lastDone;

    @Column(name = "last_cycles")
    private Cycles lastCycles;

    @Column(name = "last_hours")
    private Hours lastHours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "against",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inspection_against_repair"))
    private RepairRow repair;

    /** An empty row, filled in through its setters. */
    public InspectionRow() {}

    public RepairId getAgainst() {
        return RepairId.parse(against);
    }

    public InspectionRow setAgainst(RepairId against) {
        this.against = against.toString();
        return this;
    }

    public PanelId getPart() {
        return part;
    }

    public InspectionRow setPart(PanelId part) {
        this.part = part;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public InspectionRow setMethod(String method) {
        this.method = method;
        return this;
    }

    public Optional<Cycles> getThresholdCycles() {
        return Optional.ofNullable(thresholdCycles);
    }

    public InspectionRow setThresholdCycles(Cycles thresholdCycles) {
        this.thresholdCycles = thresholdCycles;
        return this;
    }

    public Optional<Integer> getThresholdMonths() {
        return Optional.ofNullable(thresholdMonths);
    }

    public InspectionRow setThresholdMonths(Integer thresholdMonths) {
        this.thresholdMonths = thresholdMonths;
        return this;
    }

    public Optional<Cycles> getRepeatCycles() {
        return Optional.ofNullable(repeatCycles);
    }

    public InspectionRow setRepeatCycles(Cycles repeatCycles) {
        this.repeatCycles = repeatCycles;
        return this;
    }

    public LocalDate getEmbodied() {
        return embodied;
    }

    public InspectionRow setEmbodied(LocalDate embodied) {
        this.embodied = embodied;
        return this;
    }

    public Cycles getAtCycles() {
        return atCycles;
    }

    public InspectionRow setAtCycles(Cycles atCycles) {
        this.atCycles = atCycles;
        return this;
    }

    public Hours getAtHours() {
        return atHours;
    }

    public InspectionRow setAtHours(Hours atHours) {
        this.atHours = atHours;
        return this;
    }

    public Optional<LocalDate> getLastDone() {
        return Optional.ofNullable(lastDone);
    }

    public InspectionRow setLastDone(LocalDate lastDone) {
        this.lastDone = lastDone;
        return this;
    }

    public Optional<Cycles> getLastCycles() {
        return Optional.ofNullable(lastCycles);
    }

    public InspectionRow setLastCycles(Cycles lastCycles) {
        this.lastCycles = lastCycles;
        return this;
    }

    public Optional<Hours> getLastHours() {
        return Optional.ofNullable(lastHours);
    }

    public InspectionRow setLastHours(Hours lastHours) {
        this.lastHours = lastHours;
        return this;
    }

    /** {@code <InspectionRow RP-53-0142 detailed visual>}, for a log. */
    @Override
    public String toString() {
        return "<InspectionRow " + against + " " + method + ">";
    }
}
