CREATE TABLE rental_units
(
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    landlord_id  UUID           NOT NULL,
    room_number  VARCHAR(50)    NOT NULL,
    description  TEXT,
    rent_amount  DECIMAL(12, 2) NOT NULL,
    is_available BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rental_units PRIMARY KEY (id),
    CONSTRAINT fk_rental_units_landlord FOREIGN KEY (landlord_id) REFERENCES users (id) ON DELETE CASCADE
);