package com.travelbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Transaction API tests, numbered after the required test list in the brief. */
class TransactionApiTest extends ApiTestSupport {

    private static final String FLIGHT_EXTERNAL_ID = "flight-TXL-LHR-2026-03-11";

    // 1. Create flight, hotel, rail, trip_fee, agent_call_fee, and GET returns the details
    @Test
    void createsEveryTypeAndReturnsItsDetails() {
        JsonNode flight = get("/transactions/" + create(seed("flight"))).body();
        assertThat(flight.at("/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertThat(flight.at("/metadata/origin").asText()).isEqualTo("TXL");
        assertThat(flight.at("/tax_lines/0/name").asText()).isEqualTo("Airport tax");
        assertThat(flight.at("/fee_lines/0/name").asText()).isEqualTo("YQ");

        JsonNode hotel = get("/transactions/" + create(seed("hotel"))).body();
        assertThat(hotel.at("/metadata/name").asText()).isEqualTo("Hotel Mitte London");
        assertThat(hotel.at("/metadata/address/street").asText()).isEqualTo("12 Kingsway");
        assertThat(hotel.at("/metadata/address/city").asText()).isEqualTo("London");
        assertThat(hotel.at("/metadata/address/country").asText()).isEqualTo("GB");

        JsonNode rail = get("/transactions/" + create(seed("rail"))).body();
        assertThat(rail.at("/metadata/legs")).hasSize(2);
        assertThat(rail.at("/metadata/legs/1/destination").asText()).isEqualTo("Paris Nord");

        JsonNode tripFee = get("/transactions/" + create(seed("trip_fee"))).body();
        assertThat(tripFee.at("/metadata/fee_kind").asText()).isEqualTo("trip_fee");

        JsonNode agentCallFee = get("/transactions/" + create(seed("agent_call_fee"))).body();
        assertThat(agentCallFee.at("/metadata/fee_kind").asText()).isEqualTo("agent_call_fee");
    }

    // Unknown type or fee_kind is rejected with 400 (brief: "Unknown type or unknown navan_fee.fee_kind: 400").
    @Test
    void rejectsUnknownTypeAndFeeKind() {
        assertThat(post("/transactions", seed("flight").put("type", "boat")).status()).isEqualTo(400);

        ObjectNode fee = seed("trip_fee");
        ((ObjectNode) fee.get("metadata")).put("fee_kind", "lounge_fee");
        assertThat(post("/transactions", fee).status()).isEqualTo(400);
    }

    // 2. Duplicate POST: no second row, 409 with the existing id
    @Test
    void duplicateExternalIdReturnsExistingId() {
        String id = create(seed("flight"));

        Response duplicate = post("/transactions", seed("duplicate_flight"));

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("existing_id").asText()).isEqualTo(id);
        assertThat(rowsWithExternalId(FLIGHT_EXTERNAL_ID)).isEqualTo(1);
    }

    // 6. 10 simultaneous duplicate POSTs leave exactly 1 row
    @Test
    void concurrentDuplicatePostsCreateExactlyOneRow() throws Exception {
        int requests = 10;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            statuses.add(pool.submit(() -> {
                startTogether.await();
                return post("/transactions", seed("flight")).status();
            }));
        }
        startTogether.countDown();

        List<Integer> results = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            results.add(status.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).containsOnly(201, 409);
        assertThat(results).filteredOn(status -> status == 201).hasSize(1);
        assertThat(rowsWithExternalId(FLIGHT_EXTERNAL_ID)).isEqualTo(1);
    }

    // 7. PATCH an invoiced transaction returns 409, invoice unchanged
    @Test
    void patchOfInvoicedTransactionIsRefused() {
        String flightId = create(seed("flight"));
        String invoiceId = post("/invoices", Map.of("transaction_id", flightId)).body().get("id").asText();

        Response patch = patch("/transactions/" + flightId, Map.of("total", 999.00));

        assertThat(patch.status()).isEqualTo(409);
        assertThat(get("/transactions/" + flightId).body().get("total").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(get("/invoices/" + invoiceId).body().at("/totals/grand_total").decimalValue())
                .isEqualByComparingTo("220.00");
    }

    // 8. DELETE an invoiced transaction returns 409, row still there
    @Test
    void deleteOfInvoicedTransactionIsRefused() {
        String flightId = create(seed("flight"));
        post("/invoices", Map.of("transaction_id", flightId));

        assertThat(delete("/transactions/" + flightId).status()).isEqualTo(409);
        assertThat(get("/transactions/" + flightId).status()).isEqualTo(200);
    }

    // 10. List defaults to 50, and limit=1000 returns 400
    @Test
    void listIsPaginatedWithDefaultAndMaximumLimit() {
        for (int i = 0; i < 55; i++) {
            create(seed("trip_fee").put("external_id", "page-test-" + i));
        }

        JsonNode firstPage = get("/transactions").body();
        assertThat(firstPage.get("items")).hasSize(50);
        assertThat(firstPage.get("limit").asInt()).isEqualTo(50);
        String cursor = firstPage.get("next_cursor").asText();

        JsonNode secondPage = get("/transactions?cursor=" + cursor).body();
        assertThat(secondPage.get("items")).hasSize(5);
        assertThat(secondPage.has("next_cursor")).isFalse();

        assertThat(get("/transactions?limit=1000").status()).isEqualTo(400);
    }

    private int rowsWithExternalId(String externalId) {
        return jdbc.queryForObject("SELECT count(*) FROM transactions WHERE external_id = ?", Integer.class, externalId);
    }
}
