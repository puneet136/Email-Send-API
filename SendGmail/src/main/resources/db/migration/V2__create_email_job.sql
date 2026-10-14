CREATE TABLE email_job (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           campaign_id BIGINT NOT NULL,
                           recipient_email VARCHAR(254)
                                  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
                           status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                           attempt_count INT NOT NULL DEFAULT 0,
                           created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                           updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                               ON UPDATE CURRENT_TIMESTAMP(6),

                           PRIMARY KEY (id),

                           CONSTRAINT fk_email_job_campaign
                               FOREIGN KEY (campaign_id)
                                   REFERENCES campaign (id),

                           CONSTRAINT uq_email_job_campaign_recipient
                               UNIQUE (campaign_id, recipient_email)
) ENGINE = InnoDB;