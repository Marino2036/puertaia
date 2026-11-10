CREATE TABLE IF NOT EXISTS door_commands (
    id BIGSERIAL PRIMARY KEY,
    door_id VARCHAR(80) NOT NULL,
    action VARCHAR(30) NOT NULL,
    seconds INTEGER NOT NULL DEFAULT 5,
    requested_by VARCHAR(120) NOT NULL DEFAULT 'Administrador',
    method VARCHAR(80) NOT NULL DEFAULT 'APP_CLOUD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_door_commands_pending
ON door_commands (door_id, status, id);

CREATE TABLE IF NOT EXISTS device_state (
    door_id VARCHAR(80) PRIMARY KEY,
    online BOOLEAN NOT NULL DEFAULT FALSE,
    wifi_ssid VARCHAR(120),
    ip_local VARCHAR(64),
    firmware VARCHAR(80),
    last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS access_events (
    id BIGSERIAL PRIMARY KEY,
    door_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    username VARCHAR(120),
    method VARCHAR(80),
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
