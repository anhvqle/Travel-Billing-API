# Travel Billing API

Take-home B: flight, hotel, rail and Navan fee transactions with snapshot invoices.
Java 21, Spring Boot 3.5, SQLite.

## Requirements

- JDK 21 (`brew install --cask temurin@21`)
- No Maven install needed: `./mvnw` downloads it

## Run

```bash
./mvnw spring-boot:run        # API on http://localhost:8080, data in ./travel-billing.db
curl localhost:8080/health    # {"status":"ok"}
```

## Try it

```bash
# Create the seed transactions
for k in flight hotel rail trip_fee agent_call_fee; do
  jq ".$k" fixtures/task-b/seed-payloads.json \
    | curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @-; echo
done

# Same external_id again -> 409 with the existing id (still one row)
jq '.duplicate_flight' fixtures/task-b/seed-payloads.json \
  | curl -s -X POST localhost:8080/transactions -H 'Content-Type: application/json' -d @-

# Get, list, update, delete
curl -s localhost:8080/transactions/<id>
curl -s "localhost:8080/transactions?limit=2"     # next page: add &cursor=<next_cursor>
curl -s -X PATCH localhost:8080/transactions/<id> -H 'Content-Type: application/json' -d '{"total": 230.00}'
curl -s -X DELETE localhost:8080/transactions/<id>
```

## Rules

| Case | Response |
|---|---|
| Duplicate `external_id` | 409 with `existing_id` |
| Bad input (unknown `type`/`fee_kind`, missing field, >2 decimals) | 400 |
| Unknown id | 404 |
| PATCH/DELETE a transaction that is on an invoice | 409 |
| List `limit` | default 50, max 100, over 100 → 400 |

Money is stored as integer cents and handled as `BigDecimal` (no floating point).
