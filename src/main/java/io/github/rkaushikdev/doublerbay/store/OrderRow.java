package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One question out to the manufacturer and whatever came back. One card gets at most one order a day.
 *
 * <p>The conditions an approval came back with are newline separated in one column. There are never
 * more than a handful and nothing queries them, so a child table would be a join for something only
 * ever read whole.
 */
@Entity
@Table(
        name = "engineering_order",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_engineering_order_against",
                        columnNames = {"against", "raised"}),
        indexes = {
            @Index(name = "ix_engineering_order_against", columnList = "against"),
            @Index(name = "ix_engineering_order_raised", columnList = "raised"),
            @Index(name = "ix_engineering_order_state", columnList = "state")
        })
public class OrderRow extends Recorded {

    @Id
    @Column(name = "reference", length = 16, nullable = false)
    private String reference;

    @Column(name = "against", length = 16, nullable = false)
    private FindingId against;

    @Column(name = "question", length = 512, nullable = false)
    private String question;

    @Column(name = "raised", nullable = false)
    private LocalDate raised;

    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "sent")
    private LocalDate sent;

    @Column(name = "answered")
    private LocalDate answered;

    @Column(name = "sheet", length = 16)
    private String sheet;

    @Column(name = "approved_category", length = 1)
    private String approvedCategory;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "conditions", length = 1024)
    private String conditions;

    /** An empty row, filled in through its setters. */
    public OrderRow() {}

    public String getReference() {
        return reference;
    }

    public OrderRow setReference(String reference) {
        this.reference = reference;
        return this;
    }

    public FindingId getAgainst() {
        return against;
    }

    public OrderRow setAgainst(FindingId against) {
        this.against = against;
        return this;
    }

    public String getQuestion() {
        return question;
    }

    public OrderRow setQuestion(String question) {
        this.question = question;
        return this;
    }

    public LocalDate getRaised() {
        return raised;
    }

    public OrderRow setRaised(LocalDate raised) {
        this.raised = raised;
        return this;
    }

    public String getState() {
        return state;
    }

    public OrderRow setState(String state) {
        this.state = state;
        return this;
    }

    public Optional<LocalDate> getSent() {
        return Optional.ofNullable(sent);
    }

    public OrderRow setSent(LocalDate sent) {
        this.sent = sent;
        return this;
    }

    public Optional<LocalDate> getAnswered() {
        return Optional.ofNullable(answered);
    }

    public OrderRow setAnswered(LocalDate answered) {
        this.answered = answered;
        return this;
    }

    public Optional<String> getSheet() {
        return Optional.ofNullable(sheet);
    }

    public OrderRow setSheet(String sheet) {
        this.sheet = sheet;
        return this;
    }

    public Optional<String> getApprovedCategory() {
        return Optional.ofNullable(approvedCategory);
    }

    public OrderRow setApprovedCategory(String approvedCategory) {
        this.approvedCategory = approvedCategory;
        return this;
    }

    public Optional<LocalDate> getValidUntil() {
        return Optional.ofNullable(validUntil);
    }

    public OrderRow setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
        return this;
    }

    public Optional<String> getConditions() {
        return Optional.ofNullable(conditions);
    }

    public OrderRow setConditions(String conditions) {
        this.conditions = conditions;
        return this;
    }

    /** {@code <OrderRow EO-2025-0001 raised>}, for a log. */
    @Override
    public String toString() {
        return "<OrderRow " + reference + " " + state + ">";
    }
}
