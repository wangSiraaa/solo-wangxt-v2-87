-- Fictional fisheries quota ledger.
-- Weights: NUMERIC(14,3) kilograms; application maps to java.math.BigDecimal.
-- This is a demonstration system with invented species/permit rules.
-- It does NOT connect to any regulatory system and is NOT a real fishing permit.

CREATE TABLE IF NOT EXISTS vessel (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    home_port       VARCHAR(120) NOT NULL,
    permit_note     VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS species (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    common_name     VARCHAR(120) NOT NULL,
    -- Fictional rule text, e.g. permitted areas/seasons; demo only.
    rule_note       VARCHAR(300) NOT NULL
);

CREATE TABLE IF NOT EXISTS sea_area (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS fishing_season (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    season_start    DATE NOT NULL,
    season_end      DATE NOT NULL
);

-- One balance row per vessel + species + sea area + season.
CREATE TABLE IF NOT EXISTS quota_balance (
    id              BIGSERIAL PRIMARY KEY,
    vessel_id       BIGINT NOT NULL REFERENCES vessel(id),
    species_id      BIGINT NOT NULL REFERENCES species(id),
    area_id         BIGINT NOT NULL REFERENCES sea_area(id),
    season_id       BIGINT NOT NULL REFERENCES fishing_season(id),
    remaining_kg    NUMERIC(14,3) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_quota_balance UNIQUE (vessel_id, species_id, area_id, season_id),
    CONSTRAINT ck_quota_balance_remaining CHECK (remaining_kg >= 0)
);

-- Append-only ledger: every balance change must have an entry here.
CREATE TABLE IF NOT EXISTS ledger_entry (
    id              BIGSERIAL PRIMARY KEY,
    balance_id      BIGINT NOT NULL REFERENCES quota_balance(id),
    entry_type      VARCHAR(20) NOT NULL
                    CHECK (entry_type IN ('ALLOCATION','LANDING','TRANSFER_OUT','TRANSFER_IN')),
    delta_kg        NUMERIC(14,3) NOT NULL,       -- signed: debit negative, credit positive
    remaining_kg    NUMERIC(14,3) NOT NULL,       -- balance snapshot after posting
    entry_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    ref_doc         VARCHAR(80),
    landing_id      BIGINT,
    transfer_id     BIGINT,
    note            VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS idx_ledger_balance ON ledger_entry(balance_id);

CREATE TABLE IF NOT EXISTS voyage (
    id              BIGSERIAL PRIMARY KEY,
    voyage_no       VARCHAR(40)  NOT NULL UNIQUE,
    vessel_id       BIGINT NOT NULL REFERENCES vessel(id),
    season_id       BIGINT NOT NULL REFERENCES fishing_season(id),
    departed_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DECLARED'
                    CHECK (status IN ('DECLARED','LANDED','CLOSED')),
    declared_note   VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS voyage_item (
    id              BIGSERIAL PRIMARY KEY,
    voyage_id       BIGINT NOT NULL REFERENCES voyage(id),
    species_id      BIGINT NOT NULL REFERENCES species(id),
    area_id         BIGINT NOT NULL REFERENCES sea_area(id),
    estimated_kg    NUMERIC(14,3) NOT NULL CHECK (estimated_kg >= 0),
    CONSTRAINT uq_voyage_item UNIQUE (voyage_id, species_id, area_id)
);

CREATE TABLE IF NOT EXISTS landing (
    id              BIGSERIAL PRIMARY KEY,
    certificate_no  VARCHAR(60) NOT NULL UNIQUE,
    voyage_id       BIGINT NOT NULL REFERENCES voyage(id),
    landed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    port_name       VARCHAR(120) NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS landing_item (
    id              BIGSERIAL PRIMARY KEY,
    landing_id      BIGINT NOT NULL REFERENCES landing(id),
    voyage_item_id  BIGINT NOT NULL REFERENCES voyage_item(id),
    actual_kg       NUMERIC(14,3) NOT NULL CHECK (actual_kg >= 0),
    CONSTRAINT uq_landing_item UNIQUE (landing_id, voyage_item_id)
);

-- A transfer is ONE credit/debit pair: both source and destination are mandatory.
CREATE TABLE IF NOT EXISTS quota_transfer (
    id              BIGSERIAL PRIMARY KEY,
    transfer_no     VARCHAR(40) NOT NULL UNIQUE,
    source_balance_id  BIGINT NOT NULL REFERENCES quota_balance(id),
    dest_balance_id    BIGINT NOT NULL REFERENCES quota_balance(id),
    species_id      BIGINT NOT NULL REFERENCES species(id),
    area_id         BIGINT NOT NULL REFERENCES sea_area(id),
    season_id       BIGINT NOT NULL REFERENCES fishing_season(id),
    amount_kg       NUMERIC(14,3) NOT NULL CHECK (amount_kg > 0),
    effective_from  DATE NOT NULL,
    effective_to    DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','REVOKED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    note            VARCHAR(300),
    CONSTRAINT ck_transfer_direction CHECK (source_balance_id <> dest_balance_id),
    CONSTRAINT ck_transfer_period CHECK (effective_from <= effective_to)
);
