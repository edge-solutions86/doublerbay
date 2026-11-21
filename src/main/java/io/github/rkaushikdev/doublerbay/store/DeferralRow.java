package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A decision to keep flying with a damage, and the limit on it, keyed on the card it is against.
 *
 * <p>The due list wants the live ones in the order they were granted, which is one index
 * ({@code ix_deferral_live}) rather than a scan and a sort.
 */
@Entity
@Table(
        name = "deferral",
        indexes = {
            @Index(name = "ix_deferral_granted", columnList = "granted"),
            @Index(name = "ix_deferral_live", columnList = "lifted, granted")
        })
public class DeferralRow extends Recorded {

    @Id
    @Column(name = "against", length = 16, nullable = false)
    private String against;

    @Column(name = "granted", nullable = false)
    private LocalDate granted;

    @Column(name = "at_cycles", nullable = false)
    private Cycles atCycles;

    @Column(name = "at_hours", nullable = false)
    private Hours atHours;

    @Column(name = "limit_cycles")
    private Cycles limitCycles;

    @Column(name = "limit_hours")
    private Hours limitHours;

    @Column(name = "limit_months")
    private Integer limitMonths;

    @Column(name = "limit_days")
    private Integer limitDays;

    @Column(name = "method", length = 32, nullable = false)
    private String method;

    @Column(name = "granted_by", length = 64, nullable = false)
    private String grantedBy;

    @Column(name = "reason", length = 256, nullable = false)
    private String reason = "";

    @Column(name = "lifted")
    private LocalDate lifted;

    /** An empty row, filled in through its setters. */
    public DeferralRow() {}

    public FindingId getAgainst() {
        return FindingId.parse(against);
    }

    public DeferralRow setAgainst(FindingId against) {
        this.against = against.toString();
        return this;
    }

    public LocalDate getGranted() {
        return granted;
    }

    public DeferralRow setGranted(LocalDate granted) {
        this.granted = granted;
        return this;
    }

    public Cycles getAtCycles() {
        return atCycles;
    }

    public DeferralRow setAtCycles(Cycles atCycles) {
        this.atCycles = atCycles;
        return this;
    }

    public Hours getAtHours() {
        return atHours;
    }

    public DeferralRow setAtHours(Hours atHours) {
        this.atHours = atHours;
        return this;
    }

    public Optional<Cycles> getLimitCycles() {
        return Optional.ofNullable(limitCycles);
    }

    public DeferralRow setLimitCycles(Cycles limitCycles) {
        this.limitCycles = limitCycles;
        return this;
    }

    public Optional<Hours> getLimitHours() {
        return Optional.ofNullable(limitHours);
    }

    public DeferralRow setLimitHours(Hours limitHours) {
        this.limitHours = limitHours;
        return this;
    }

    public Optional<Integer> getLimitMonths() {
        return Optional.ofNullable(limitMonths);
    }

    public DeferralRow setLimitMonths(Integer limitMonths) {
        this.limitMonths = limitMonths;
        return this;
    }

    public Optional<Integer> getLimitDays() {
        return Optional.ofNullable(limitDays);
    }

    public DeferralRow setLimitDays(Integer limitDays) {
        this.limitDays = limitDays;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public DeferralRow setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public DeferralRow setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public DeferralRow setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public Optional<LocalDate> getLifted() {
        return Optional.ofNullable(lifted);
    }

    public DeferralRow setLifted(LocalDate lifted) {
        this.lifted = lifted;
        return this;
    }

    /** {@code <DeferralRow DR-2025-004182 granted 2025-03-14>}, for a log. */
    @Override
    public String toString() {
        return "<DeferralRow " + against + " granted " + granted + ">";
    }
}
