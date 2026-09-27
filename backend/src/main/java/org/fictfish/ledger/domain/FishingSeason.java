package org.fictfish.ledger.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "fishing_season")
public class FishingSeason {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "season_start", nullable = false)
    private LocalDate seasonStart;

    @Column(name = "season_end", nullable = false)
    private LocalDate seasonEnd;

    protected FishingSeason() {
    }

    public FishingSeason(String code, LocalDate seasonStart, LocalDate seasonEnd) {
        this.code = code;
        this.seasonStart = seasonStart;
        this.seasonEnd = seasonEnd;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getSeasonStart() {
        return seasonStart;
    }

    public LocalDate getSeasonEnd() {
        return seasonEnd;
    }
}
