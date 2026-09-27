package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "voyage")
public class Voyage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voyage_no", nullable = false, unique = true, length = 40)
    private String voyageNo;

    @Column(name = "vessel_id", nullable = false)
    private Long vesselId;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "departed_at", nullable = false)
    private OffsetDateTime departedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoyageStatus status = VoyageStatus.DECLARED;

    @Column(name = "declared_note", length = 300)
    private String declaredNote;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "voyage_id", nullable = false)
    private List<VoyageItem> items = new ArrayList<>();

    protected Voyage() {
    }

    public Voyage(String voyageNo, Long vesselId, Long seasonId, OffsetDateTime departedAt,
                  String declaredNote) {
        this.voyageNo = voyageNo;
        this.vesselId = vesselId;
        this.seasonId = seasonId;
        this.departedAt = departedAt;
        this.declaredNote = declaredNote;
    }

    public Long getId() {
        return id;
    }

    public String getVoyageNo() {
        return voyageNo;
    }

    public Long getVesselId() {
        return vesselId;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public OffsetDateTime getDepartedAt() {
        return departedAt;
    }

    public VoyageStatus getStatus() {
        return status;
    }

    public String getDeclaredNote() {
        return declaredNote;
    }

    public List<VoyageItem> getItems() {
        return items;
    }

    public void addItem(VoyageItem item) {
        this.items.add(item);
    }

    public void markLanded() {
        this.status = VoyageStatus.LANDED;
    }
}
