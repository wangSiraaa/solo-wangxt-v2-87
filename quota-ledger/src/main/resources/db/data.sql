-- ============================================================================
-- Fictional demo data. Species, vessels, areas and permit rules are invented
-- for this exercise; this is NOT a real fishing permit and does not connect to
-- any regulatory system.
-- All NUMERIC weights here are kilograms with 3 decimal places (BigDecimal).
-- ============================================================================

INSERT INTO species(code, name, note) VALUES
    ('YLJ', '玉鳞鲛', '虚构物种：体侧有玉色鳞纹'),
    ('CHX', '赤纹鳕', '虚构物种：红色纵纹小型鳕类'),
    ('XHT', '星河鲀', '虚构物种：腹部有星点状斑纹')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sea_area(code, name, note) VALUES
    ('CL-N', '沧澜北渔区', '虚构海区'),
    ('CL-E', '碧涛东渔区', '虚构海区'),
    ('CL-S', '栖霞南渔区', '虚构海区')
ON CONFLICT (code) DO NOTHING;

INSERT INTO season(code, name, starts_on, ends_on) VALUES
    ('S2026-Q1', '2026年第一作业季', DATE '2026-01-01', DATE '2026-03-31'),
    ('S2026-Q3', '2026年第三作业季', DATE '2026-07-01', DATE '2026-09-30')
ON CONFLICT (code) DO NOTHING;

-- Fictional permit matrix: species x area x season.
INSERT INTO species_permit(species_code, area_code, season_code) VALUES
    ('YLJ', 'CL-N', 'S2026-Q3'),
    ('YLJ', 'CL-E', 'S2026-Q3'),
    ('CHX', 'CL-N', 'S2026-Q3'),
    ('CHX', 'CL-E', 'S2026-Q1'),
    ('XHT', 'CL-S', 'S2026-Q3')
ON CONFLICT DO NOTHING;

INSERT INTO vessel(code, name, home_port) VALUES
    ('V01', '沧澜01', '北岚港'),
    ('V02', '碧涛07', '东屿港'),
    ('V03', '栖霞12', '南澳港')
ON CONFLICT (code) DO NOTHING;

-- Fixed ids make the ISSUE ledger rows deterministic.
INSERT INTO quota_account(id, vessel_code, species_code, area_code, season_code, issued_qty) VALUES
    (1, 'V01', 'YLJ', 'CL-N', 'S2026-Q3', 1000.000),
    (2, 'V01', 'CHX', 'CL-N', 'S2026-Q3',  600.000),
    (3, 'V02', 'YLJ', 'CL-E', 'S2026-Q3',  800.000),
    (4, 'V02', 'CHX', 'CL-E', 'S2026-Q1',  300.000),
    (5, 'V03', 'YLJ', 'CL-N', 'S2026-Q3',  500.000),
    (6, 'V03', 'XHT', 'CL-S', 'S2026-Q3',  400.000),
    (7, 'V01', 'YLJ', 'CL-E', 'S2026-Q3',    0.000),
    (8, 'V02', 'YLJ', 'CL-N', 'S2026-Q3',  200.000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quota_ledger_entry(id, account_id, event_type, amount, balance_after, ref_type, memo) VALUES
    (1, 1, 'ISSUE', 1000.000, 1000.000, 'SEED', '初始额度发放'),
    (2, 2, 'ISSUE',  600.000,  600.000, 'SEED', '初始额度发放'),
    (3, 3, 'ISSUE',  800.000,  800.000, 'SEED', '初始额度发放'),
    (4, 4, 'ISSUE',  300.000,  300.000, 'SEED', '初始额度发放'),
    (5, 5, 'ISSUE',  500.000,  500.000, 'SEED', '初始额度发放'),
    (6, 6, 'ISSUE',  400.000,  400.000, 'SEED', '初始额度发放'),
    (7, 7, 'ISSUE',    0.000,    0.000, 'SEED', '初始额度账户（待调入）'),
    (8, 8, 'ISSUE',  200.000,  200.000, 'SEED', '初始额度发放')
ON CONFLICT (id) DO NOTHING;

-- Keep identity sequences ahead of explicit ids.
SELECT setval(pg_get_serial_sequence('quota_account','id'),
              (SELECT max(id) FROM quota_account));
SELECT setval(pg_get_serial_sequence('quota_ledger_entry','id'),
              (SELECT max(id) FROM quota_ledger_entry));
