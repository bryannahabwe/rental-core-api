CREATE TABLE tenants
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    landlord_id UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(255),
    address     TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT fk_tenants_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE
);