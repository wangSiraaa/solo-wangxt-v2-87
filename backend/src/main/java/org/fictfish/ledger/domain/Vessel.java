package org.fictfish.ledger.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "vessel")
public class Vessel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "home_port", nullable = false, length = 120)
    private String homePort;

    @Column(name = "permit_note", nullable = false, length = 200)
    private String permitNote;

    protected Vessel() {
    }

    public Vessel(String code, String name, String homePort, String permitNote) {
        this.code = code;
        this.name = name;
        this.homePort = homePort;
        this.permitNote = permitNote;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getHomePort() {
        return homePort;
    }

    public String getPermitNote() {
        return permitNote;
    }
}
