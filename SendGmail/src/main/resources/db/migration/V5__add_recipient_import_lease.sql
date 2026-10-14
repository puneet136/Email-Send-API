ALTER TABLE recipient_import
    ADD COLUMN worker_token VARCHAR(36)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN heartbeat_at DATETIME(6) NULL,
    ADD COLUMN lease_expires_at DATETIME(6) NULL,
    ADD COLUMN claim_count INT NOT NULL DEFAULT 0,
    ADD INDEX idx_recipient_import_status_lease (
        status,
        lease_expires_at
    );