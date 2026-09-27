package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "landing_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_landing_item",
                columnNames = {"landing_id", "voyage_item_id"}))
public class LandingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voyage_item_id", nullable = false)
    private Long voyageItemId;

    @Column(name = "actual_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal actualKg;

    protected LandingItem() {
    }

    public LandingItem(Long voyageItemId, BigDecimal actualKg) {
        this.voyageItemId = voyageItemId;
        this.actualKg = actualKg;
    }

    public Long getId() {
        return id;
    }

    public Long getVoyageItemId() {
        return voyageItemId;
    }

    public BigDecimal getActualKg() {
        return actualKg;
    }
}
