package com.travelbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** Invoice API tests, numbered after the required test list in the brief. */
class InvoiceApiTest extends ApiTestSupport {

    // 3. Trip invoice with all 5, grand total 685.70 EUR
    @Test
    void tripInvoiceMatchesExpectedTotalsAndRollups() throws IOException {
        JsonNode expected = objectMapper.readTree(new File("fixtures/task-b/expected-invoice.json"));

        Response created = post("/invoices", Map.of("transaction_ids", createTrip()));
        assertThat(created.status()).isEqualTo(201);
        JsonNode invoice = get("/invoices/" + created.body().get("id").asText()).body();

        assertThat(invoice.get("currency").asText()).isEqualTo("EUR");
        assertThat(invoice.get("lines")).hasSize(5);
        expected.get("totals").properties().forEach(total ->
                assertThat(invoice.at("/totals/" + total.getKey()).decimalValue())
                        .as(total.getKey())
                        .isEqualByComparingTo(total.getValue().decimalValue()));
        assertSameMoneyLines(invoice.get("tax_rollup"), expected.get("tax_rollup_example"));
        assertSameMoneyLines(invoice.get("fee_rollup"), expected.get("fee_rollup_example"));
    }

    // 4. Change the flight after invoicing, and the invoice still shows 220.00
    @Test
    void invoiceKeepsItsSnapshotWhenTheTransactionChanges() {
        String flightId = create(seed("flight"));
        String invoiceId = post("/invoices", Map.of("transaction_id", flightId)).body().get("id").asText();

        jdbc.update("UPDATE transactions SET total_cents = 99900 WHERE id = ?", flightId);
        assertThat(get("/transactions/" + flightId).body().get("total").decimalValue()).isEqualByComparingTo("999.00");

        JsonNode invoice = get("/invoices/" + invoiceId).body();
        assertThat(invoice.at("/totals/flight").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(invoice.at("/lines/0/snapshot/total").decimalValue()).isEqualByComparingTo("220.00");
    }

    // 5. EUR + USD invoice returns 409
    @Test
    void mixedCurrencyInvoiceIsRefused() {
        String eurFlight = create(seed("flight"));
        String usdHotel = create(seed("hotel").put("currency", "USD").put("external_id", "hotel-usd"));

        Response response = post("/invoices", Map.of("transaction_ids", List.of(eurFlight, usdHotel)));

        assertThat(response.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices", Integer.class)).isZero();
    }

    // Same id twice on one invoice is 409; an unknown id is 404.
    @Test
    void duplicateOrUnknownTransactionIdIsRefused() {
        String flightId = create(seed("flight"));

        assertThat(post("/invoices", Map.of("transaction_ids", List.of(flightId, flightId))).status()).isEqualTo(409);
        assertThat(post("/invoices", Map.of("transaction_id", "txn_does_not_exist")).status()).isEqualTo(404);
    }

    // 9. DELETE an invoice, and the transactions still GET
    @Test
    void deletingInvoiceKeepsTransactions() {
        String flightId = create(seed("flight"));
        String invoiceId = post("/invoices", Map.of("transaction_id", flightId)).body().get("id").asText();

        assertThat(delete("/invoices/" + invoiceId).status()).isEqualTo(204);
        assertThat(get("/invoices/" + invoiceId).status()).isEqualTo(404);
        assertThat(get("/transactions/" + flightId).status()).isEqualTo(200);
        assertThat(delete("/transactions/" + flightId).status()).isEqualTo(204);
    }

    private List<String> createTrip() {
        return List.of("flight", "hotel", "rail", "trip_fee", "agent_call_fee").stream()
                .map(name -> create(seed(name)))
                .toList();
    }

    /** Same names, rates and amounts in the same order; money compared by value (15.0 equals 15.00). */
    private void assertSameMoneyLines(JsonNode actual, JsonNode expected) {
        assertThat(actual).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode line = actual.get(i);
            JsonNode want = expected.get(i);
            assertThat(line.get("name").asText()).isEqualTo(want.get("name").asText());
            assertThat(line.get("amount").decimalValue()).isEqualByComparingTo(want.get("amount").decimalValue());
            assertThat(line.has("rate")).isEqualTo(want.has("rate"));
            if (want.has("rate")) {
                assertThat(line.get("rate").decimalValue()).isEqualByComparingTo(want.get("rate").decimalValue());
            }
        }
    }
}
