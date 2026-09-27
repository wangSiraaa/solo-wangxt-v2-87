package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "landing")
public class Landing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_no", nullable = false, unique = true, length = 60)
    private String certificateNo;

    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;

    @Column(name = "landed_at", nullable = false)
    private OffsetDateTime landedAt;

    @Column(name = "port_name", nullable = false, length = 120)
    private String portName;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "landing_id", nullable = false)
    private List<LandingItem> items = new ArrayList<>();

    protected Landing() {
    }

    public Landing(String certificateNo, Long voyageId, OffsetDateTime landedAt,
                   String portName, OffsetDateTime recordedAt) {
        this.certificateNo = certificateNo;
        this.voyageId = voyageId;
        this.landedAt = landedAt;
        this.portName = portName;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCertificateNo() {
        return certificateNo;
    }

    public Long getVoyageId() {
        return voyageId;
    }

    public OffsetDateTime getLandedAt() {
        return landedAt;
    }

    public String getPortName() {
        return portName;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public List<LandingItem> getItems() {
        return items;
    }

    public void addItem(LandingItem item) {
        this.items.add(item);
    }
}
