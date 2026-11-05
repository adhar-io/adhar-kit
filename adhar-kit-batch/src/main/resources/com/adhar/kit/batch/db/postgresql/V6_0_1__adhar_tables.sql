-- The kit's own tables in the JobRepository schema (adhar-kit-batch, design 0025 section 5.8).
-- All three ship in one migration because a released migration never changes: the execution
-- lease (the Kubernetes runner) and the dead letters (fault-tolerant steps) are written by
-- later kit code against exactly this shape. Times are TIMESTAMP WITH TIME ZONE, so instances
-- in different JVM time zones agree on them.

-- Embedded scheduler (adhar.batch.scheduler.lock=jdbc): one row per scheduled job. A holder
-- keeps LOCKED_UNTIL in the future for as long as its run lasts; an expired row may be taken
-- over. Replaces the runtime CREATE TABLE of the first kit release.
CREATE TABLE ADHAR_SCHEDULER_LOCK (
    LOCK_NAME VARCHAR(200) NOT NULL PRIMARY KEY,
    LOCKED_BY VARCHAR(255) NOT NULL,
    LOCKED_AT TIMESTAMP WITH TIME ZONE NOT NULL,
    LOCKED_UNTIL TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Kubernetes runner: who owns a running execution, and since when it last proved alive. A
-- STARTED execution is recovered only when its owner pod is gone or HEARTBEAT_AT is stale.
CREATE TABLE ADHAR_BATCH_EXECUTION_LEASE (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    OWNER_POD VARCHAR(253) NOT NULL,
    OWNER_UID VARCHAR(64),
    NODE VARCHAR(253),
    ACQUIRED_AT TIMESTAMP WITH TIME ZONE NOT NULL,
    HEARTBEAT_AT TIMESTAMP WITH TIME ZONE NOT NULL,
    LAST_COMMIT_AT TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ADHAR_LEASE_EXEC_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES ${tablePrefix}JOB_EXECUTION (JOB_EXECUTION_ID) ON DELETE CASCADE
);

-- Fault-tolerant steps: the items a step skipped, kept for inspection and replay. Rows outlive
-- the JobRepository metadata retention deletes, so they carry the job name and do not reference
-- the execution. ITEM_TYPE is the item's Java type, needed to replay ITEM; PHASE is read,
-- process or write (a read failure has no item).
CREATE TABLE ADHAR_BATCH_DEAD_LETTER (
    ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    JOB_NAME VARCHAR(100) NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    BUSINESS_DATE DATE,
    PHASE VARCHAR(10) NOT NULL,
    ITEM_TYPE VARCHAR(500),
    ITEM JSONB,
    ERROR_CLASS VARCHAR(500) NOT NULL,
    ERROR_MESSAGE VARCHAR(2500),
    CREATED_AT TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    REPLAYED_AT TIMESTAMP WITH TIME ZONE,
    REPLAY_EXECUTION_ID BIGINT,
    CONSTRAINT ADHAR_DEAD_LETTER_PHASE_CK CHECK (PHASE IN ('read', 'process', 'write'))
);

CREATE INDEX ADHAR_DEAD_LETTER_EXEC_IDX ON ADHAR_BATCH_DEAD_LETTER (JOB_EXECUTION_ID);
-- Replay candidates of a step.
CREATE INDEX ADHAR_DEAD_LETTER_PENDING_IDX ON ADHAR_BATCH_DEAD_LETTER (JOB_NAME, STEP_NAME, ID)
    WHERE REPLAYED_AT IS NULL;
