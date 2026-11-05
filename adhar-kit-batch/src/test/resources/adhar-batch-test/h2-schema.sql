-- Test schema for H2 contexts: the kit only migrates PostgreSQL, so on H2 the scheduler lock
-- table is created here, with the DDL JdbcSchedulerLock.verify() prints, plus the business tables
-- of the test jobs.
CREATE TABLE IF NOT EXISTS ADHAR_SCHEDULER_LOCK (
    LOCK_NAME VARCHAR(200) NOT NULL PRIMARY KEY,
    LOCKED_BY VARCHAR(255) NOT NULL,
    LOCKED_AT TIMESTAMP WITH TIME ZONE NOT NULL,
    LOCKED_UNTIL TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS SOURCE_ITEMS (ID BIGINT NOT NULL PRIMARY KEY);
-- No key on purpose: a duplicate write must be countable, not rejected.
CREATE TABLE IF NOT EXISTS TARGET_ITEMS (ID BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS SLOT_RUNS (RUNNER VARCHAR(100) NOT NULL, SCHEDULED_TIME VARCHAR(40) NOT NULL);
