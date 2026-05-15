-- NeuralOps TimescaleDB initialization
-- This script runs once when the TimescaleDB container is first created.
-- The TimescaleDB extension is pre-installed in the timescale/timescaledb Docker image.
-- Hypertable creation is handled by Flyway migrations in the metrics-service.

CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
