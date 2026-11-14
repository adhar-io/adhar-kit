-- Business tables of the batch self-tests. TARGET_ITEMS has no key on purpose: a duplicate write
-- must be countable, not rejected.
CREATE TABLE IF NOT EXISTS source_items (id BIGINT NOT NULL PRIMARY KEY);
CREATE TABLE IF NOT EXISTS target_items (id BIGINT NOT NULL);
