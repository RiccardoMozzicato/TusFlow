CREATE TABLE users
(
    id            UUID         NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    organization  VARCHAR(255) NOT NULL,
    role          SMALLINT     NOT NULL,
    department    VARCHAR(255),
    storage_quota BIGINT,
    created_at    TIMESTAMP WITHOUT TIME ZONE,
    updated_at    TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE users
    ADD CONSTRAINT uc_users_email UNIQUE (email);

DROP TABLE "user" CASCADE;