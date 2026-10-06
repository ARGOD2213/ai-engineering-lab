-- Runs once, the first time the Docker volume is initialised.
-- The application's Flyway migration (V1) also runs CREATE EXTENSION IF NOT EXISTS, so the
-- service works against any PostgreSQL that has pgvector available (e.g. a managed database).
CREATE EXTENSION IF NOT EXISTS vector;
