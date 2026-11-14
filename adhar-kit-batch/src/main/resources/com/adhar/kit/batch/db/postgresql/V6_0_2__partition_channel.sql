-- The partition channel of remote partitioning (adhar-kit-batch, design 0025 sections 5.8 and 5.10):
-- a manager writes one row per partition request, and a worker claims a row by deleting it
-- (Spring Integration's PostgresChannelMessageStoreQueryProvider polls with a single
-- DELETE ... RETURNING ... FOR UPDATE SKIP LOCKED, so two workers never claim the same request).
-- Rows are short-lived: the JobRepository, not this table, guarantees that a partition runs once.
--
-- Spring Integration 7.1's own INT_CHANNEL_MESSAGE and INT_MESSAGE_SEQ from
-- org/springframework/integration/jdbc/schema-postgresql.sql, unchanged and unqualified: Flyway's
-- default schema puts them in the batch schema, where JdbcPartitionTransport reads them as
-- <schema>.INT_CHANNEL_MESSAGE. Only these two objects: the transport uses a QueueChannel over
-- JdbcChannelMessageStore and none of the other message stores. Every service gets the (empty)
-- table; only a remote-partitioned job (transport jdbc, the only transport in Phase 2) writes to it.
-- The sequence default is bound to the sequence when the table is created, so it resolves in the
-- batch schema whatever the connection's search_path is later.

CREATE SEQUENCE INT_MESSAGE_SEQ START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE INT_CHANNEL_MESSAGE (
    MESSAGE_ID CHAR(36) NOT NULL,
    GROUP_KEY CHAR(36) NOT NULL,
    CREATED_DATE BIGINT NOT NULL,
    MESSAGE_PRIORITY BIGINT,
    MESSAGE_SEQUENCE BIGINT NOT NULL DEFAULT nextval('INT_MESSAGE_SEQ'),
    MESSAGE_CONTENT BYTEA,
    REGION VARCHAR(100) NOT NULL,
    CONSTRAINT INT_CHANNEL_MESSAGE_PK PRIMARY KEY (REGION, GROUP_KEY, CREATED_DATE, MESSAGE_SEQUENCE)
);

CREATE INDEX INT_CHANNEL_MSG_DELETE_IDX ON INT_CHANNEL_MESSAGE (REGION, GROUP_KEY, MESSAGE_ID);
