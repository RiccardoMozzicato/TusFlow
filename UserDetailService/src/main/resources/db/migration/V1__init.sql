CREATE TABLE "user"
(
    id            UUID         NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    organization  VARCHAR(255) NOT NULL,
    role          SMALLINT     NOT NULL,
    department    VARCHAR(255),
    storage_quota VARCHAR(255),
    created_at    TIMESTAMP WITHOUT TIME ZONE,
    updated_at    TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_user PRIMARY KEY (id)
);