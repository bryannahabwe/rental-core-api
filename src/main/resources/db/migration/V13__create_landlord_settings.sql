CREATE TABLE landlord_settings (
                                   id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                   landlord_id       UUID        NOT NULL REFERENCES users(id) UNIQUE,
                                   company_name      VARCHAR(255),
                                   address           TEXT,
                                   logo_url          TEXT,
                                   receipt_prefix    VARCHAR(10)  NOT NULL DEFAULT 'RCP',
                                   next_receipt_no   INT          NOT NULL DEFAULT 1,
                                   receipt_numbering VARCHAR(20)  NOT NULL DEFAULT 'AUTO',
                                   receipt_footer    TEXT         DEFAULT 'Thank you for your business',
                                   receipt_style     VARCHAR(20)  NOT NULL DEFAULT 'DIGITAL',
                                   created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
                                   updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);