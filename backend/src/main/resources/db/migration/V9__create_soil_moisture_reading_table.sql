CREATE TABLE soil_moisture_reading (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    sensor_id VARCHAR(255) NOT NULL,
    raw_adc INTEGER NOT NULL CHECK (raw_adc BETWEEN 0 AND 4095),
    millivolts DOUBLE PRECISION,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_soil_moisture_reading_sensor_id_received_at ON soil_moisture_reading (sensor_id, received_at DESC);
