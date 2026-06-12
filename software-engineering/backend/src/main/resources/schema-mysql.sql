CREATE TABLE IF NOT EXISTS station_state (
    id VARCHAR(64) PRIMARY KEY,
    business_date DATE,
    now_time DATETIME(6),
    fast_seq INT NOT NULL,
    slow_seq INT NOT NULL,
    detail_seq INT NOT NULL,
    waiting_capacity INT NOT NULL DEFAULT 10,
    pile_slot_capacity INT NOT NULL DEFAULT 3,
    fast_pile_count INT NOT NULL DEFAULT 3,
    slow_pile_count INT NOT NULL DEFAULT 2,
    parking_rate DECIMAL(10, 4) NOT NULL DEFAULT 0.5,
    parking_grace_period_minutes INT NOT NULL DEFAULT 5,
    station_state VARCHAR(16) NOT NULL DEFAULT 'RUNNING'
);

CREATE TABLE IF NOT EXISTS charging_piles (
    id VARCHAR(16) PRIMARY KEY,
    mode VARCHAR(16),
    power_kw INT NOT NULL,
    status VARCHAR(16),
    current_request_id VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS charging_requests (
    id VARCHAR(64) PRIMARY KEY,
    vehicle_id VARCHAR(64),
    mode VARCHAR(16),
    requested_kwh DECIMAL(18, 4),
    queue_no VARCHAR(32),
    status VARCHAR(32),
    pile_id VARCHAR(16),
    created_at DATETIME(6),
    started_at DATETIME(6),
    stopped_at DATETIME(6),
    charged_kwh DECIMAL(18, 4),
    queue_kind VARCHAR(32),
    queue_position INT NOT NULL
);

CREATE TABLE IF NOT EXISTS charging_details (
    detail_no VARCHAR(64) PRIMARY KEY,
    generated_at DATETIME(6),
    pile_id VARCHAR(16),
    vehicle_id VARCHAR(64),
    request_id VARCHAR(64),
    charged_kwh DECIMAL(18, 4),
    duration_minutes BIGINT NOT NULL,
    started_at DATETIME(6),
    stopped_at DATETIME(6),
    charge_fee DECIMAL(18, 2),
    service_fee DECIMAL(18, 2),
    total_fee DECIMAL(18, 2),
    price_breakdown VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS fault_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pile_id VARCHAR(16),
    started_at DATETIME(6),
    recover_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    vehicle_id VARCHAR(64),
    phone VARCHAR(32),
    role VARCHAR(16) NOT NULL,
    created_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS parking_fees (
    id VARCHAR(64) PRIMARY KEY,
    vehicle_id VARCHAR(64) NOT NULL,
    pile_id VARCHAR(16),
    bill_detail_no VARCHAR(64) NOT NULL UNIQUE,
    over_time_minutes BIGINT NOT NULL,
    rate_per_minute DECIMAL(10, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    generated_at DATETIME(6),
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at DATETIME(6)
);
