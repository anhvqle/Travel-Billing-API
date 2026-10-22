# Architecture

The code is split by feature: `transaction/` and `invoice/`. Each feature has a service (the rules) and a
repository (the SQL). `api/` only handles HTTP. The database is SQLite, accessed with plain SQL.

## Money

Amounts are stored as whole cents (`22000` = 220.00) and handled as `BigDecimal` in Java, never as `float`.
More than 2 decimals is rejected with 400. Adding cents is always exact, so invoice totals never drift.

## No duplicate transactions

`external_id` has a unique index, so the database itself refuses a second row with the same value.

A create runs `INSERT ... ON CONFLICT DO NOTHING`. If nothing was inserted, the transaction already exists,
and the API returns **409** with `existing_id`.

This also covers two POSTs arriving at the same moment: only one insert can win, the other gets 409.
A test sends 10 at once and checks that exactly one row exists.

Every database transaction starts with `BEGIN IMMEDIATE`, which takes the write lock right away.
So "check, then write" steps (like "is this transaction on an invoice? if not, update it") can't be
interrupted by another request. A request that has to wait retries for up to 10 seconds.

## Tables

**Transactions**
- `transactions`: the fields every type has (`external_id`, `type`, `currency`, `total_cents`, `occurred_at`).
- `transaction_tax_lines` and `transaction_fee_lines`: separate, because a tax has a rate and a fee doesn't,
  and the invoice totals them separately.
- One table per type for the details:
  - flight: `flight_details` + `flight_coupons` (origin, destination, flight numbers)
  - hotel: `hotel_details` + `hotel_extras` (name, address, dates)
  - rail: `rail_legs`
  - Navan fee: `navan_fee_details` (`fee_kind` is `trip_fee` or `agent_call_fee`)

**Invoices**
- `invoices`: one row per invoice (`currency`, `total_cents`).
- `invoice_lines`: one row per transaction on the invoice, with a full copy of it in `snapshot_json`.
  An index on `transaction_id` makes "is this transaction on an invoice?" a quick lookup.
- `invoice_tax_rollup` (grouped by name + rate) and `invoice_fee_rollup` (grouped by name): the tax and fee
  totals, saved when the invoice is made.

Each detail table has a fixed `type` column in its foreign key, so hotel details can't be attached to a flight.
Checks in the schema catch bad values, e.g. `check_out` must be after `check_in`.

I chose real tables over one JSON column so the database can check the data. The cost is more tables to read
for a GET, but each one is a fast lookup by key.

## Invoices don't change after they're made

An invoice is like a printed receipt. There are two protections:

1. **Snapshot.** When an invoice is created, each transaction is copied into it, and the tax and fee totals
   are saved too. Reading an invoice never looks at `transactions` again, so even a change made directly in
   the database can't affect it.
2. **Lock.** The API refuses to PATCH or DELETE a transaction that is on any invoice (409). This keeps a
   transaction from disagreeing with what was billed. To change one, remove it from the invoice first.
   The database also blocks deleting it (`ON DELETE RESTRICT`).

Other rules:
- The same transaction id twice in one request is 409, so nothing is counted twice.
- Mixed currencies are 409.
- `PATCH /invoices/{id}` rebuilds the whole invoice from the current transactions, all in one database transaction.
- A transaction can be on more than one invoice (e.g. if an invoice is re-issued).

## High volume

What keeps it fast:
- Lookups by id use the primary key. "Is this transaction on an invoice?" uses an index on
  `invoice_lines.transaction_id`.
- Lists are paged with a cursor (`WHERE id > ? ORDER BY id LIMIT ...`): 50 by default, 100 at most,
  over 100 is a 400. Ids are in time order, so page 1000 is as fast as page 1. An offset would get slower
  the further you go.
- Lists return short summaries in one query per page, not one query per row.

What breaks first: **writes**. SQLite lets only one write happen at a time for the whole database. With lots of
POSTs and PATCHes at once, requests queue up, and after 10 seconds of waiting they fail.
Reading a single transaction or invoice also waits, because it runs in a transaction so it reads a consistent
picture. Lists don't wait.

What I'd do next: move to Postgres. It locks single rows instead of the whole database, so many writes can
happen at once, and reads don't wait for writes. The schema and SQL would mostly stay the same.
