package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A quota transfer is one credit/debit pair between two balances.
 * Species, sea area and season must match on both legs — quota cannot be
 * used across mismatching species/area/season.
 */
@Entity
@Table(name = "quota_transfer")
public class QuotaTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true, length = 40)
    private String transferNo;

    @Column(name = "source_balance_id", nullable = false)
    private Long sourceBalanceId;

    @Column(name = "dest_balance_id", nullable = false)
    private Long destBalanceId;

    @Column(name = "species_id", nullable = false)
    private Long speciesId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "amount_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal amountKg;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(length = 300)
    private String note;

    protected QuotaTransfer() {
    }

    public QuotaTransfer(String transferNo, Long sourceBalanceId, Long destBalanceId,
                         Long speciesId, Long areaId, Long seasonId, BigDecimal amountKg,
                         LocalDate effectiveFrom, LocalDate effectiveTo, String note) {
        this.transferNo = transferNo;
        this.sourceBalanceId = sourceBalanceId;
        this.destBalanceId = destBalanceId;
        this.speciesId = speciesId;
        this.areaId = areaId;
        this.seasonId = seasonId;
        this.amountKg = amountKg;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.note = note;
        this.status = TransferStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransferNo() {
        return transferNo;
    }

    public Long getSourceBalanceId() {
        return sourceBalanceId;
    }

    public Long getDestBalanceId() {
        return destBalanceId;
    }

    public Long getSpeciesId() {
        return speciesId;
    }

    public Long getAreaId() {
        return areaId;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public BigDecimal getAmountKg() {
        return amountKg;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getNote() {
        return note;
    }
}
