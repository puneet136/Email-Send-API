CREATE TABLE campaign (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          name VARCHAR(150) NOT NULL,
                          subject VARCHAR(200) NOT NULL,
                          body TEXT NOT NULL,
                          status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                          created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                              ON UPDATE CURRENT_TIMESTAMP(6),

                          PRIMARY KEY (id)
) ENGINE = InnoDB;