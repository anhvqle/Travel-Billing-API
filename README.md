# Travel Billing API

Take-home B: flight, hotel, rail and Navan fee transactions with snapshot invoices.
Java 21, Spring Boot 3.5, SQLite.

## Requirements

- JDK 21 (`brew install --cask temurin@21`)
- No Maven install needed: `./mvnw` downloads it

## Setup

```bash
./mvnw spring-boot:run        # API on http://localhost:8080, data in ./travel-billing.db
curl localhost:8080/health    # {"status":"ok"}
```

**Run the examples below in another terminal from the project folder.** They use `jq` (preinstalled on macOS).

The examples assume an empty database. To start fresh, stop the API and delete the database file:
```bash
rm -f travel-billing.db travel-billing.db-wal travel-billing.db-shm
```

## Transactions

**Create the five seed transactions**
```bash
for k in flight hotel rail trip_fee agent_call_fee; do
  jq ".$k" fixtures/task-b/seed-payloads.json \
    | curl -s -o /dev/null -w "$k: %{http_code}\n" -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @-
done
```
Expected: `201` for each.

**Duplicate `external_id` is rejected**
```bash
jq '.duplicate_flight' fixtures/task-b/seed-payloads.json \
  | curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @-
```
Expected: `409` `{"error":"conflict","message":"a transaction with external_id flight-TXL-LHR-2026-03-11 already exists","existing_id":"txn_..."}`

**Get one transaction**
```bash
FLIGHT=$(curl -s "localhost:8080/transactions?limit=100" | jq -r '.items[] | select(.external_id=="flight-TXL-LHR-2026-03-11") | .id')
curl -s localhost:8080/transactions/$FLIGHT | jq
```
Expected: `200` with `tax_lines`, `fee_lines` and `metadata.coupons` (flight `BA982`).

**List (cursor pagination)**
```bash
curl -s "localhost:8080/transactions?limit=2" | jq
```
Expected: `200` `{"items":[...2 transactions...],"next_cursor":"txn_...","limit":2}`. Pass `&cursor=<next_cursor>` for the next page.

**List with `limit` above 100 is rejected**
```bash
curl -s "localhost:8080/transactions?limit=1000" | jq
```
Expected: `400` `{"error":"validation_error","message":"limit must be between 1 and 100, got 1000"}`

**Update**
```bash
HOTEL=$(curl -s "localhost:8080/transactions?limit=100" | jq -r '.items[] | select(.type=="hotel") | .id')
curl -s -X PATCH localhost:8080/transactions/$HOTEL -H 'Content-Type: application/json' -d '{"total": 331.70}' | jq
```
Expected: `200` with the updated transaction. `type`, `external_id` and `currency` cannot be changed (`400`).

**Delete**
```bash
TEMP=$(jq '.trip_fee | .external_id = "fee-to-delete"' fixtures/task-b/seed-payloads.json \
  | curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @- | jq -r .id)
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE localhost:8080/transactions/$TEMP
```
Expected: `204`. A transaction that is on an invoice cannot be updated or deleted: `409`.

## Invoices

**Create an invoice for the whole trip**
```bash
IDS=$(curl -s "localhost:8080/transactions?limit=100" | jq -c '{transaction_ids: [.items[].id]}')
INVOICE=$(curl -s -X POST localhost:8080/invoices -H 'Content-Type: application/json' -d "$IDS" | jq -r .id)
```
Expected: `201`. A single transaction also works: `{"transaction_id": "<id>"}`.

**Get the invoice**
```bash
curl -s localhost:8080/invoices/$INVOICE | jq '{totals, tax_rollup, fee_rollup}'
```
Expected:
```json
{
  "totals": { "flight": 220.00, "hotel": 331.70, "rail": 94.00, "trip_fee": 25.00, "agent_call_fee": 15.00, "grand_total": 685.70 },
  "tax_rollup": [ { "name": "Airport tax", "amount": 15.00 }, { "name": "VAT", "rate": 0.07, "amount": 21.70 } ],
  "fee_rollup": [ { "name": "YQ", "amount": 25.00 }, { "name": "Booking fee", "amount": 4.00 } ]
}
```
Each line also carries a full `snapshot` of its transaction, so later changes to a transaction never alter the invoice.

**Billed transactions are protected**
```bash
curl -s -X PATCH localhost:8080/transactions/$FLIGHT -H 'Content-Type: application/json' -d '{"total": 999.00}'
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE localhost:8080/transactions/$FLIGHT
```
Expected: `409` `{"error":"conflict","message":"transaction txn_... is on an invoice and cannot be changed"}`, then `409`.

## Rules

| Case | Response |
|---|---|
| Duplicate `external_id` | 409 with `existing_id` |
| Bad input (unknown `type`/`fee_kind`, missing field, >2 decimals) | 400 |
| Unknown id | 404 |
| PATCH/DELETE a transaction that is on an invoice | 409 |
| List `limit` | default 50, max 100, over 100 → 400 |
| Invoice with mixed currencies | 409 |
| Same transaction id twice on one invoice | 409 |
| Unknown transaction id on an invoice | 404 |

An invoice copies its transactions when it is created, so later changes to a transaction never alter an existing invoice.

Money is stored as integer cents and handled as `BigDecimal` (no floating point).
