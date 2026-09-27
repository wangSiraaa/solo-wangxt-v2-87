package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Quota balance for exactly one vessel + species + sea area + season.
 * Only mutated together with an append-only {@link LedgerEntry}.
 */
@Entity
@Table(name = "quota_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quota_balance",
                columnNames = {"vessel_id", "species_id", "area_id", "season_id"}))
public class QuotaBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vessel_id", nullable = false)
    private Long vesselId;

    @Column(name = "species_id", nullable = false)
    private Long speciesId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "remaining_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal remainingKg;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected QuotaBalance() {
    }

    public QuotaBalance(Long vesselId, Long speciesId, Long areaId, Long seasonId,
                        BigDecimal remainingKg) {
        this.vesselId = vesselId;
        this.speciesId = speciesId;
        this.areaId = areaId;
        this.seasonId = seasonId;
        this.remainingKg = remainingKg;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getVesselId() {
        return vesselId;
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

    public BigDecimal getRemainingKg() {
        return remainingKg;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void applyDelta(BigDecimal deltaKg) {
        this.remainingKg = this.remainingKg.add(deltaKg);
        this.updatedAt = OffsetDateTime.now();
    }
}
