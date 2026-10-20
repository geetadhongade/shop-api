-- Optimistic locking: every update increases "version" by 1.
-- If two requests change the same row at the same moment, the second one sees the version has
-- changed and fails, instead of silently overwriting the first one's change.
ALTER TABLE orders   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customer ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE product  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
