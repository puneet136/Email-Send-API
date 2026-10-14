ALTER TABLE campaign
    ADD COLUMN started_at DATETIME(6) NULL,
    ADD COLUMN completed_at DATETIME(6) NULL,
    ADD INDEX idx_campaign_status_id (status, id);

ALTER TABLE email_job
    ADD COLUMN worker_token VARCHAR(36)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN lease_expires_at DATETIME(6) NULL,
    ADD COLUMN next_attempt_at DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN last_attempt_at DATETIME(6) NULL,
    ADD COLUMN sent_at DATETIME(6) NULL,
    ADD COLUMN last_error VARCHAR(1000) NULL,
    ADD INDEX idx_email_job_campaign_due (
        campaign_id,
        status,
        next_attempt_at,
        id
    ),
    ADD INDEX idx_email_job_status_lease (
        status,
        lease_expires_at,
        id
    );