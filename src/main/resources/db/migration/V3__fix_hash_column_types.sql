-- V2 declared content_hash/declared_hash as CHAR(64). Hibernate's schema
-- validator checks the JDBC type code derived from the Java field type, not
-- any columnDefinition override, and a plain String field validates against
-- VARCHAR — so CHAR(64) (Postgres reports it as bpchar) never matches no
-- matter what the entity annotation says. Align the DB to VARCHAR(64)
-- instead of fighting the validator on the entity side.

ALTER TABLE files ALTER COLUMN content_hash TYPE VARCHAR(64);
ALTER TABLE uploads ALTER COLUMN declared_hash TYPE VARCHAR(64);
