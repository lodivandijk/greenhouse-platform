CREATE TABLE device (
    device_id VARCHAR(255) PRIMARY KEY,
    software_version VARCHAR(100),
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    last_ip_address VARCHAR(45),
    last_signal_strength_dbm INTEGER,
    last_uptime_seconds BIGINT,
    heartbeat_count BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL
);
