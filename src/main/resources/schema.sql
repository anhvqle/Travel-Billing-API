-- Money is stored as INTEGER cents (scale 2): 220.00 EUR is stored as 22000. No floating point.
-- Timestamps are stored as ISO-8601 TEXT in UTC (e.g. 2026-03-11T06:40:00Z), which sorts correctly as text.

-- One row per travel transaction (flight, hotel, rail, navan_fee): the shared "header" fields.
CREATE TABLE IF NOT EXISTS transactions (
    id          TEXT    PRIMARY KEY,
    external_id TEXT    NOT NULL,
    type        TEXT    NOT NULL CHECK (type IN ('flight', 'hotel', 'rail', 'navan_fee')),
    currency    TEXT    NOT NULL CHECK (length(currency) = 3 AND currency = upper(currency)),
    occurred_at TEXT    NOT NULL,
    total_cents INTEGER NOT NULL,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

-- The duplicate guard: the database itself refuses a second row with the same external_id,
-- even if two requests try to insert it at the same moment.
CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_external_id ON transactions (external_id);

-- Lets the type-specific tables point at the pair (id, type), so the database itself
-- guarantees e.g. hotel details can only belong to a transaction whose type is 'hotel'.
CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_id_type ON transactions (id, type);

-- Government taxes (VAT, airport tax). Many rows per transaction, kept in the order they were sent.
CREATE TABLE IF NOT EXISTS transaction_tax_lines (
    transaction_id TEXT    NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    rate           TEXT,
    amount_cents   INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, position)
);

-- Carrier/merchant fees (YQ surcharge, booking fee). Separate from taxes on purpose.
CREATE TABLE IF NOT EXISTS transaction_fee_lines (
    transaction_id TEXT    NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    amount_cents   INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, position)
);

-- ---------------------------------------------------------------------------
-- Type-specific details ("class table inheritance").
-- Each table has a fixed `type` column and a foreign key on (transaction_id, type),
-- so the database refuses e.g. hotel details on a flight transaction.
-- ---------------------------------------------------------------------------

-- flight: origin/destination of the whole ticket + base fare.
CREATE TABLE IF NOT EXISTS flight_details (
    transaction_id TEXT    NOT NULL PRIMARY KEY,
    type           TEXT    NOT NULL DEFAULT 'flight' CHECK (type = 'flight'),
    origin         TEXT    NOT NULL,
    destination    TEXT    NOT NULL,
    fare_cents     INTEGER NOT NULL,
    FOREIGN KEY (transaction_id, type) REFERENCES transactions (id, type) ON DELETE CASCADE
);

-- flight coupons: one row per flown segment. A round trip has two or more.
CREATE TABLE IF NOT EXISTS flight_coupons (
    transaction_id TEXT    NOT NULL,
    type           TEXT    NOT NULL DEFAULT 'flight' CHECK (type = 'flight'),
    position       INTEGER NOT NULL,
    flight_number  TEXT    NOT NULL,
    carrier        TEXT,
    from_airport   TEXT    NOT NULL,
    to_airport     TEXT    NOT NULL,
    departure      TEXT    NOT NULL,
    arrival        TEXT    NOT NULL,
    PRIMARY KEY (transaction_id, position),
    FOREIGN KEY (transaction_id, type) REFERENCES transactions (id, type) ON DELETE CASCADE
);

-- hotel: property name + address and the stay.
CREATE TABLE IF NOT EXISTS hotel_details (
    transaction_id            TEXT    NOT NULL PRIMARY KEY,
    type                      TEXT    NOT NULL DEFAULT 'hotel' CHECK (type = 'hotel'),
    name                      TEXT    NOT NULL,
    street                    TEXT    NOT NULL,
    city                      TEXT    NOT NULL,
    country                   TEXT    NOT NULL,
    check_in                  TEXT    NOT NULL,
    check_out                 TEXT    NOT NULL CHECK (check_out > check_in),
    nights                    INTEGER NOT NULL CHECK (nights > 0),
    room_rate_per_night_cents INTEGER NOT NULL,
    FOREIGN KEY (transaction_id, type) REFERENCES transactions (id, type) ON DELETE CASCADE
);

-- hotel extras (breakfast, parking...). Optional, many per stay.
CREATE TABLE IF NOT EXISTS hotel_extras (
    transaction_id TEXT    NOT NULL,
    type           TEXT    NOT NULL DEFAULT 'hotel' CHECK (type = 'hotel'),
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    amount_cents   INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, position),
    FOREIGN KEY (transaction_id, type) REFERENCES transactions (id, type) ON DELETE CASCADE
);

-- rail legs: one row per train. A journey with a change of train is two legs.
CREATE TABLE IF NOT EXISTS rail_legs (
    transaction_id TEXT    NOT NULL,
    type           TEXT    NOT NULL DEFAULT 'rail' CHECK (type = 'rail'),
    position       INTEGER NOT NULL,
    origin         TEXT    NOT NULL,
    destination    TEXT    NOT NULL,
    departure      TEXT    NOT NULL,
    arrival        TEXT    NOT NULL,
    train_number   TEXT,
    travel_class   TEXT,
    PRIMARY KEY (transaction_id, position),
    FOREIGN KEY (transaction_id, type) REFERENCES transactions (id, type) ON DELETE CASCADE
);

-- navan_fee: Navan's own charges. Only the known fee kinds are allowed.
CREATE TABLE IF NOT EXISTS navan_fee_details (
    transaction_id      TEXT NOT NULL PRIMARY KEY,
    type                TEXT NOT NULL DEFAULT 'navan_fee' CHECK (type = 'navan_fee'),
    fee_kind            TEXT NOT NULL CHECK (fee_kind IN ('trip_fee', 'agent_call_fee')),
    description         TEXT,
    related_external_id TEXT,
    FOREIGN KEY (transaction_id, type) REFERENCES transactions (id, type) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Invoices: a frozen snapshot. Everything an invoice shows is COPIED here when it
-- is created (or rebuilt by PATCH); later edits to transactions never change it.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS invoices (
    id          TEXT    PRIMARY KEY,
    currency    TEXT    NOT NULL CHECK (length(currency) = 3 AND currency = upper(currency)),
    total_cents INTEGER NOT NULL,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

-- One line per transaction on the invoice.
--   snapshot_json: a full copy of the transaction (metadata, tax_lines, fee_lines) as it was when invoiced.
--   transaction_id: which transaction it came from. The foreign key (no CASCADE) means the database
--                   refuses to delete a transaction that is on an invoice.
CREATE TABLE IF NOT EXISTS invoice_lines (
    invoice_id     TEXT    NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    transaction_id TEXT    NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,
    type           TEXT    NOT NULL,
    total_cents    INTEGER NOT NULL,
    snapshot_json  TEXT    NOT NULL,
    PRIMARY KEY (invoice_id, position),
    UNIQUE (invoice_id, transaction_id)
);

-- Makes "is this transaction on any invoice?" a fast index lookup (checked before PATCH/DELETE of a transaction).
CREATE INDEX IF NOT EXISTS ix_invoice_lines_transaction_id ON invoice_lines (transaction_id);

-- Taxes added up across the whole invoice, grouped by (name, rate).
CREATE TABLE IF NOT EXISTS invoice_tax_rollup (
    invoice_id   TEXT    NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    rate         TEXT,
    amount_cents INTEGER NOT NULL,
    PRIMARY KEY (invoice_id, position)
);

-- Fees added up across the whole invoice, grouped by name.
CREATE TABLE IF NOT EXISTS invoice_fee_rollup (
    invoice_id   TEXT    NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    amount_cents INTEGER NOT NULL,
    PRIMARY KEY (invoice_id, position)
);
