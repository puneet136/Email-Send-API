CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    display_name VARCHAR(100) NOT NULL,

    email VARCHAR(254)
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_0900_as_cs
        NOT NULL,

    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at TIMESTAMP(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uq_app_user_email
        UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;


ALTER TABLE campaign
    ADD COLUMN owner_user_id BIGINT NULL,

    ADD INDEX idx_campaign_owner_id (
        owner_user_id,
        id
    ),

    ADD CONSTRAINT fk_campaign_owner
        FOREIGN KEY (owner_user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT;