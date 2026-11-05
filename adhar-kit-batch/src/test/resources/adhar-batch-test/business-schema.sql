-- Business tables of the test jobs (PostgreSQL contexts; the kit migrates its own schema).
CREATE TABLE IF NOT EXISTS SOURCE_ITEMS (ID BIGINT NOT NULL PRIMARY KEY);
-- No key on purpose: a duplicate write must be countable, not rejected.
CREATE TABLE IF NOT EXISTS TARGET_ITEMS (ID BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS SLOT_RUNS (RUNNER VARCHAR(100) NOT NULL, SCHEDULED_TIME VARCHAR(40) NOT NULL);
