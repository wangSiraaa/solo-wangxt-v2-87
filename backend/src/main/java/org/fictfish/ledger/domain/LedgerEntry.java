package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Append-only ledger row. delta_kg is signed (debit negative, credit positive). */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerType entryType;

    @Column(name = "delta_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal deltaKg;

    @Column(name = "remaining_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal remainingKg;

    @Column(name = "entry_date", nullable = false)
    private OffsetDateTime entryDate;

    @Column(name = "ref_doc", length = 80)
    private String refDoc;

    @Column(name = "landing_id")
    private Long landingId;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(length = 300)
    private String note;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long balanceId, LedgerType entryType, BigDecimal deltaKg,
                       BigDecimal remainingKg, OffsetDateTime entryDate, String refDoc,
                       Long landingId, Long transferId, String note) {
        this.balanceId = balanceId;
        this.entryType = entryType;
        this.deltaKg = deltaKg;
        this.remainingKg = remainingKg;
        this.entryDate = entryDate;
        this.refDoc = refDoc;
        this.landingId = landingId;
        this.transferId = transferId;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Long getBalanceId() {
        return balanceId;
    }

    public LedgerType getEntryType() {
        return entryType;
    }

    public BigDecimal getDeltaKg() {
        return deltaKg;
    }

    public BigDecimal getRemainingKg() {
        return remainingKg;
    }

    public OffsetDateTime getEntryDate() {
        return entryDate;
    }

    public String getRefDoc() {
        return refDoc;
    }

    public Long getLandingId() {
        return landingId;
    }

    public Long getTransferId() {
        return transferId;
    }

    public String getNote() {
        return note;
    }
}
