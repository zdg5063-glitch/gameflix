-- Reference schema for the README (documentation only; ddl-auto=update creates the table).
-- TODO: make sure the columns match your Module 3 ERD for User.
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL, -- sized for the hash ({bcrypt} + 60 chars = 68), not the password
    display_name  VARCHAR(50)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;
