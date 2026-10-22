package com.travelbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Base class for API tests: starts the real app on a random port with its own SQLite file,
 * wipes the database before every test, and offers small helpers to call the HTTP API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.db.path=target/test-travel-billing.db")
public abstract class ApiTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    private RestClient client;
    private JsonNode seeds;

    /** The status code and JSON body of one HTTP call. */
    protected record Response(int status, JsonNode body) {
    }

    @BeforeEach
    void startFromEmptyDatabase() throws IOException {
        jdbc.update("DELETE FROM invoices");      // cascades to invoice lines and rollups
        jdbc.update("DELETE FROM transactions");  // cascades to tax/fee lines and type details
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        seeds = objectMapper.readTree(new File("fixtures/task-b/seed-payloads.json"));
    }

    /** A fresh copy of one seed payload, e.g. seed("flight"), that a test may change freely. */
    protected ObjectNode seed(String name) {
        return seeds.get(name).deepCopy();
    }

    /** Creates a transaction and returns its server id. Fails the test if it was not created. */
    protected String create(JsonNode payload) {
        Response response = post("/transactions", payload);
        assertThat(response.status()).as(response.toString()).isEqualTo(201);
        return response.body().get("id").asText();
    }

    protected Response get(String path) {
        return call(HttpMethod.GET, path, null);
    }

    protected Response post(String path, Object body) {
        return call(HttpMethod.POST, path, body);
    }

    protected Response patch(String path, Object body) {
        return call(HttpMethod.PATCH, path, body);
    }

    protected Response delete(String path) {
        return call(HttpMethod.DELETE, path, null);
    }

    /** Sends one request and returns status + body, without throwing on 4xx/5xx (tests check those). */
    private Response call(HttpMethod method, String path, Object body) {
        RestClient.RequestBodySpec request = client.method(method).uri(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((req, res) -> {
            String text = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
            return new Response(res.getStatusCode().value(), text.isEmpty() ? null : objectMapper.readTree(text));
        });
    }
}
