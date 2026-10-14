CREATE TABLE recipient_import_error (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        import_id BIGINT NOT NULL,
                                        record_number BIGINT NOT NULL,
                                        raw_value VARCHAR(512) NULL,
                                        reason VARCHAR(255) NOT NULL,

                                        created_at TIMESTAMP(6) NOT NULL
                                            DEFAULT CURRENT_TIMESTAMP(6),

                                        PRIMARY KEY (id),

                                        CONSTRAINT fk_import_error_import
                                            FOREIGN KEY (import_id)
                                                REFERENCES recipient_import (id),

                                        CONSTRAINT uq_import_error_record
                                            UNIQUE (import_id, record_number)
) ENGINE = InnoDB;