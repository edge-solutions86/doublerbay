package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Length;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One repair, with its whole drawing on the row.
 *
 * <p>There are only so many ways to draw a doubler and the numbers on one repair are not shared
 * with any other, so a scheme table would be one row per repair with a join on top of it. The clash
 * check asks for the fitted repairs on one panel of one tail and the audit for the fitted repairs on
 * one tail; both are a prefix of {@code ix_repair_fitted}.
 */
@Entity
@Table(
        name = "repair",
        indexes = {
            @Index(name = "ix_repair_against", columnList = "against"),
            @Index(name = "ix_repair_category", columnList = "category"),
            @Index(name = "ix_repair_embodied", columnList = "embodied"),
            @Index(name = "ix_repair_part", columnList = "part"),
            @Index(name = "ix_repair_serial", columnList = "serial"),
            @Index(name = "ix_repair_fitted", columnList = "serial, part, removed")
        })
public class RepairRow extends Recorded {

    @Id
    @Column(name = "reference", length = 16, nullable = false)
    private String reference;

    @Column(name = "serial", length = 8, nullable = false)
    private String serial;

    @Column(name = "part", length = 16, nullable = false)
    private PanelId part;

    @Column(name = "against", length = 16, nullable = false)
    private FindingId against;

    @Column(name = "form", length = 24, nullable = false)
    private String form;

    @Column(name = "category", length = 1, nullable = false)
    private String category;

    @Column(name = "sealed", nullable = false)
    private boolean sealed;

    @Column(name = "page", length = 16, nullable = false)
    private String page;

    @Column(name = "material", length = 24, nullable = false)
    private String material;

    @Column(name = "gauge", nullable = false)
    private Length gauge;

    @Column(name = "along", nullable = false)
    private Length along;

    @Column(name = "around", nullable = false)
    private Length around;

    @Column(name = "filler")
    private Length filler;

    @Column(name = "fastener_dash", nullable = false)
    private int fastenerDash;

    @Column(name = "rows_each_side", nullable = false)
    private int rowsEachSide;

    @Column(name = "per_row", nullable = false)
    private int perRow;

    @Column(name = "pitch", nullable = false)
    private Length pitch;

    @Column(name = "row_spacing", nullable = false)
    private Length rowSpacing;

    @Column(name = "edge_distance", nullable = false)
    private Length edgeDistance;

    @Column(name = "centre_along", nullable = false)
    private Length centreAlong;

    @Column(name = "centre_around", nullable = false)
    private Length centreAround;

    @Column(name = "embodied", nullable = false)
    private LocalDate embodied;

    @Column(name = "at_cycles", nullable = false)
    private Cycles atCycles;

    @Column(name = "at_hours", nullable = false)
    private Hours atHours;

    @Column(name = "removed")
    private LocalDate removed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "serial",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_repair_serial_aircraft"))
    private AircraftRow aircraft;

    @OneToMany(mappedBy = "repair", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionRow> inspection = new ArrayList<>();

    /** The repeat inspection this repair carries, if it carries one. Read only when asked for. */
    public Optional<InspectionRow> getInspection() {
        return inspection.stream().findFirst();
    }

    /** An empty row, filled in through its setters. */
    public RepairRow() {}

    public RepairId getReference() {
        return RepairId.parse(reference);
    }

    public RepairRow setReference(RepairId reference) {
        this.reference = reference.toString();
        return this;
    }

    public Serial getSerial() {
        return new Serial(serial);
    }

    public RepairRow setSerial(Serial serial) {
        this.serial = serial.number();
        return this;
    }

    public PanelId getPart() {
        return part;
    }

    public RepairRow setPart(PanelId part) {
        this.part = part;
        return this;
    }

    public FindingId getAgainst() {
        return against;
    }

    public RepairRow setAgainst(FindingId against) {
        this.against = against;
        return this;
    }

    public String getForm() {
        return form;
    }

    public RepairRow setForm(String form) {
        this.form = form;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public RepairRow setCategory(String category) {
        this.category = category;
        return this;
    }

    public boolean isSealed() {
        return sealed;
    }

    public RepairRow setSealed(boolean sealed) {
        this.sealed = sealed;
        return this;
    }

    public String getPage() {
        return page;
    }

    public RepairRow setPage(String page) {
        this.page = page;
        return this;
    }

    public String getMaterial() {
        return material;
    }

    public RepairRow setMaterial(String material) {
        this.material = material;
        return this;
    }

    public Length getGauge() {
        return gauge;
    }

    public RepairRow setGauge(Length gauge) {
        this.gauge = gauge;
        return this;
    }

    public Length getAlong() {
        return along;
    }

    public RepairRow setAlong(Length along) {
        this.along = along;
        return this;
    }

    public Length getAround() {
        return around;
    }

    public RepairRow setAround(Length around) {
        this.around = around;
        return this;
    }

    public Optional<Length> getFiller() {
        return Optional.ofNullable(filler);
    }

    public RepairRow setFiller(Length filler) {
        this.filler = filler;
        return this;
    }

    public int getFastenerDash() {
        return fastenerDash;
    }

    public RepairRow setFastenerDash(int fastenerDash) {
        this.fastenerDash = fastenerDash;
        return this;
    }

    public int getRowsEachSide() {
        return rowsEachSide;
    }

    public RepairRow setRowsEachSide(int rowsEachSide) {
        this.rowsEachSide = rowsEachSide;
        return this;
    }

    public int getPerRow() {
        return perRow;
    }

    public RepairRow setPerRow(int perRow) {
        this.perRow = perRow;
        return this;
    }

    public Length getPitch() {
        return pitch;
    }

    public RepairRow setPitch(Length pitch) {
        this.pitch = pitch;
        return this;
    }

    public Length getRowSpacing() {
        return rowSpacing;
    }

    public RepairRow setRowSpacing(Length rowSpacing) {
        this.rowSpacing = rowSpacing;
        return this;
    }

    public Length getEdgeDistance() {
        return edgeDistance;
    }

    public RepairRow setEdgeDistance(Length edgeDistance) {
        this.edgeDistance = edgeDistance;
        return this;
    }

    public Length getCentreAlong() {
        return centreAlong;
    }

    public RepairRow setCentreAlong(Length centreAlong) {
        this.centreAlong = centreAlong;
        return this;
    }

    public Length getCentreAround() {
        return centreAround;
    }

    public RepairRow setCentreAround(Length centreAround) {
        this.centreAround = centreAround;
        return this;
    }

    public LocalDate getEmbodied() {
        return embodied;
    }

    public RepairRow setEmbodied(LocalDate embodied) {
        this.embodied = embodied;
        return this;
    }

    public Cycles getAtCycles() {
        return atCycles;
    }

    public RepairRow setAtCycles(Cycles atCycles) {
        this.atCycles = atCycles;
        return this;
    }

    public Hours getAtHours() {
        return atHours;
    }

    public RepairRow setAtHours(Hours atHours) {
        this.atHours = atHours;
        return this;
    }

    public Optional<LocalDate> getRemoved() {
        return Optional.ofNullable(removed);
    }

    public RepairRow setRemoved(LocalDate removed) {
        this.removed = removed;
        return this;
    }

    /** {@code <RepairRow RP-53-0142 on 5310-143L>}, for a log. */
    @Override
    public String toString() {
        return "<RepairRow " + reference + " on " + part + ">";
    }
}
