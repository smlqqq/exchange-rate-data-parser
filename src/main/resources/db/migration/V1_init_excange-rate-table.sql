CREATE TABLE IF NOT EXISTS exchange_rate (
                               id SERIAL PRIMARY KEY,
                               timestamp TIMESTAMP,
                               json_data JSONB
);