CREATE TABLE observation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    temperature_celsius DOUBLE PRECISION,
    humidity_percent DOUBLE PRECISION CHECK (humidity_percent BETWEEN 0 AND 100),
    pressure_hpa DOUBLE PRECISION,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_observation_device_id_received_at ON observation (device_id, received_at DESC);
