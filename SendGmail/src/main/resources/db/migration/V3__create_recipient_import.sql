CREATE TABLE recipient_import (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  campaign_id BIGINT NOT NULL,

                                  original_filename VARCHAR(255) NOT NULL,
                                  stored_filename VARCHAR(100)
                                         CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                  file_type VARCHAR(10) NOT NULL,
                                  file_size_bytes BIGINT NOT NULL,

                                  status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',

                                  processed_rows BIGINT NOT NULL DEFAULT 0,
                                  added_rows BIGINT NOT NULL DEFAULT 0,
                                  duplicate_rows BIGINT NOT NULL DEFAULT 0,
                                  invalid_rows BIGINT NOT NULL DEFAULT 0,

                                  last_error VARCHAR(1000) NULL,

                                  created_at TIMESTAMP(6) NOT NULL
                                      DEFAULT CURRENT_TIMESTAMP(6),

                                  updated_at TIMESTAMP(6) NOT NULL
                                      DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),

                                  started_at TIMESTAMP(6) NULL,
                                  completed_at TIMESTAMP(6) NULL,

                                  PRIMARY KEY (id),

                                  CONSTRAINT fk_recipient_import_campaign
                                      FOREIGN KEY (campaign_id)
                                          REFERENCES campaign (id),

                                  CONSTRAINT uq_recipient_import_stored_filename
                                      UNIQUE (stored_filename),

                                  INDEX idx_recipient_import_status_id (status, id)
) ENGINE = InnoDB;