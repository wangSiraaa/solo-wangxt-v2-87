package org.fictfish.ledger.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "species")
public class Species {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "common_name", nullable = false, length = 120)
    private String commonName;

    @Column(name = "rule_note", nullable = false, length = 300)
    private String ruleNote;

    protected Species() {
    }

    public Species(String code, String commonName, String ruleNote) {
        this.code = code;
        this.commonName = commonName;
        this.ruleNote = ruleNote;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getRuleNote() {
        return ruleNote;
    }
}
