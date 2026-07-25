-- Sample seed script used by DatabaseSeederTest
-- Comments and blank lines should be ignored when splitting into statements.

INSERT INTO users (id, name) VALUES (1, 'Alice');

INSERT INTO users (id, name) VALUES (2, 'Bob'); -- trailing comment

DELETE FROM users WHERE id = 3;
