-- NeuralOps PostgreSQL initialization
-- This script runs once when the PostgreSQL container is first created.
-- Schema migrations after initialization are handled by Flyway in each service.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- The neuralops database is created by the POSTGRES_DB environment variable.
-- Additional databases needed by services can be added here.

\c neuralops;

-- Enable trigram index support for full-text search on trace payloads
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
