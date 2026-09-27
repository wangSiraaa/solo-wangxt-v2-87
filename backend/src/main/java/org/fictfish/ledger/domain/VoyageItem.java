package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "voyage_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_voyage_item",
                columnNames = {"voyage_id", "species_id", "area_id"}))
public class VoyageItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "species_id", nullable = false)
    private Long speciesId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "estimated_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal estimatedKg;

    protected VoyageItem() {
    }

    public VoyageItem(Long speciesId, Long areaId, BigDecimal estimatedKg) {
        this.speciesId = speciesId;
        this.areaId = areaId;
        this.estimatedKg = estimatedKg;
    }

    public Long getId() {
        return id;
    }

    public Long getSpeciesId() {
        return speciesId;
    }

    public Long getAreaId() {
        return areaId;
    }

    public BigDecimal getEstimatedKg() {
        return estimatedKg;
    }
}
