package com.travelbilling.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.transaction.metadata.FeeKind;
import com.travelbilling.transaction.metadata.FlightMetadata;
import com.travelbilling.transaction.metadata.HotelMetadata;
import com.travelbilling.transaction.metadata.NavanFeeMetadata;
import com.travelbilling.transaction.metadata.RailMetadata;
import com.travelbilling.transaction.metadata.TransactionMetadata;
import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;
import com.travelbilling.transaction.model.Transaction;
import com.travelbilling.transaction.model.TransactionSummary;
import com.travelbilling.transaction.model.TransactionType;

/**
 * All SQL for transactions. Methods that write must be called inside a database transaction
 * (the service's @Transactional), so a transaction and all its rows are saved together or not at all.
 */
@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The id of the transaction with this external_id, if one exists. Uses the unique index on external_id. */
    public Optional<String> findIdByExternalId(String externalId) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM transactions WHERE external_id = ?", String.class, externalId);
        return ids.stream().findFirst();
    }

    /** The full transaction (header, tax/fee lines and type-specific details), or empty if the id does not exist. */
    public Optional<Transaction> findById(String id) {
        List<Header> headers = jdbc.query("""
                SELECT id, external_id, type, currency, occurred_at, total_cents, created_at, updated_at
                FROM transactions
                WHERE id = ?
                """,
                (rs, rowNum) -> new Header(
                        rs.getString("id"),
                        rs.getString("external_id"),
                        TransactionType.fromValue(rs.getString("type")).orElseThrow(),
                        rs.getString("currency"),
                        Instant.parse(rs.getString("occurred_at")),
                        MoneyConverter.fromCents(rs.getLong("total_cents")),
                        Instant.parse(rs.getString("created_at")),
                        Instant.parse(rs.getString("updated_at"))),
                id);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Header header = headers.get(0);
        return Optional.of(new Transaction(
                header.id(), header.externalId(), header.type(), header.currency(), header.occurredAt(), header.total(),
                findTaxLines(id), findFeeLines(id), findMetadata(id, header.type()),
                header.createdAt(), header.updatedAt()));
    }

    /**
     * Up to `limit` transactions with an id after `afterId` (null = from the start), in id order (= creation order).
     * Always bounded by LIMIT, and the primary-key index jumps straight to the cursor.
     */
    public List<TransactionSummary> findPage(String afterId, int limit) {
        return jdbc.query("""
                SELECT id, external_id, type, currency, occurred_at, total_cents, created_at
                FROM transactions
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """,
                (rs, rowNum) -> new TransactionSummary(
                        rs.getString("id"),
                        rs.getString("external_id"),
                        TransactionType.fromValue(rs.getString("type")).orElseThrow(),
                        rs.getString("currency"),
                        Instant.parse(rs.getString("occurred_at")),
                        MoneyConverter.fromCents(rs.getLong("total_cents")),
                        Instant.parse(rs.getString("created_at"))),
                afterId == null ? "" : afterId, limit);
    }

    /** The columns of the transactions table, before the lines and details are loaded. */
    private record Header(String id, String externalId, TransactionType type, String currency, Instant occurredAt,
                          BigDecimal total, Instant createdAt, Instant updatedAt) {
    }

    private List<TaxLine> findTaxLines(String id) {
        return jdbc.query("""
                SELECT name, rate, amount_cents FROM transaction_tax_lines
                WHERE transaction_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new TaxLine(
                        rs.getString("name"),
                        rs.getString("rate") == null ? null : new BigDecimal(rs.getString("rate")),
                        MoneyConverter.fromCents(rs.getLong("amount_cents"))),
                id);
    }

    private List<FeeLine> findFeeLines(String id) {
        return jdbc.query("""
                SELECT name, amount_cents FROM transaction_fee_lines
                WHERE transaction_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new FeeLine(rs.getString("name"), MoneyConverter.fromCents(rs.getLong("amount_cents"))),
                id);
    }

    private TransactionMetadata findMetadata(String id, TransactionType type) {
        return switch (type) {
            case FLIGHT -> findFlight(id);
            case HOTEL -> findHotel(id);
            case RAIL -> findRail(id);
            case NAVAN_FEE -> findNavanFee(id);
        };
    }

    private FlightMetadata findFlight(String id) {
        List<FlightMetadata.Coupon> coupons = jdbc.query("""
                SELECT flight_number, carrier, from_airport, to_airport, departure, arrival FROM flight_coupons
                WHERE transaction_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new FlightMetadata.Coupon(
                        rs.getString("flight_number"),
                        rs.getString("carrier"),
                        rs.getString("from_airport"),
                        rs.getString("to_airport"),
                        Instant.parse(rs.getString("departure")),
                        Instant.parse(rs.getString("arrival"))),
                id);
        return jdbc.queryForObject("""
                SELECT origin, destination, fare_cents FROM flight_details WHERE transaction_id = ?
                """,
                (rs, rowNum) -> new FlightMetadata(
                        rs.getString("origin"),
                        rs.getString("destination"),
                        MoneyConverter.fromCents(rs.getLong("fare_cents")),
                        coupons),
                id);
    }

    private HotelMetadata findHotel(String id) {
        List<HotelMetadata.Extra> extras = jdbc.query("""
                SELECT name, amount_cents FROM hotel_extras
                WHERE transaction_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new HotelMetadata.Extra(
                        rs.getString("name"), MoneyConverter.fromCents(rs.getLong("amount_cents"))),
                id);
        return jdbc.queryForObject("""
                SELECT name, street, city, country, check_in, check_out, nights, room_rate_per_night_cents
                FROM hotel_details WHERE transaction_id = ?
                """,
                (rs, rowNum) -> new HotelMetadata(
                        rs.getString("name"),
                        new HotelMetadata.Address(rs.getString("street"), rs.getString("city"), rs.getString("country")),
                        LocalDate.parse(rs.getString("check_in")),
                        LocalDate.parse(rs.getString("check_out")),
                        rs.getInt("nights"),
                        MoneyConverter.fromCents(rs.getLong("room_rate_per_night_cents")),
                        extras),
                id);
    }

    private RailMetadata findRail(String id) {
        List<RailMetadata.Leg> legs = jdbc.query("""
                SELECT origin, destination, departure, arrival, train_number, travel_class FROM rail_legs
                WHERE transaction_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new RailMetadata.Leg(
                        rs.getString("origin"),
                        rs.getString("destination"),
                        Instant.parse(rs.getString("departure")),
                        Instant.parse(rs.getString("arrival")),
                        rs.getString("train_number"),
                        rs.getString("travel_class")),
                id);
        return new RailMetadata(legs);
    }

    private NavanFeeMetadata findNavanFee(String id) {
        return jdbc.queryForObject("""
                SELECT fee_kind, description, related_external_id FROM navan_fee_details WHERE transaction_id = ?
                """,
                (rs, rowNum) -> new NavanFeeMetadata(
                        FeeKind.fromValue(rs.getString("fee_kind")).orElseThrow(),
                        rs.getString("description"),
                        rs.getString("related_external_id")),
                id);
    }

    /**
     * Saves the transaction with its tax lines, fee lines and type-specific details.
     * Returns false and saves nothing if the external_id already exists: the unique index decides,
     * so this stays correct even when two requests race.
     */
    public boolean insert(Transaction transaction) {
        int inserted = jdbc.update("""
                INSERT INTO transactions (id, external_id, type, currency, occurred_at, total_cents, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (external_id) DO NOTHING
                """,
                transaction.id(),
                transaction.externalId(),
                transaction.type().value(),
                transaction.currency(),
                transaction.occurredAt().toString(),
                MoneyConverter.toCents(transaction.total(), "total"),
                transaction.createdAt().toString(),
                transaction.updatedAt().toString());
        if (inserted == 0) {
            return false;
        }
        insertLinesAndDetails(transaction);
        return true;
    }

    /** Whether any invoice line points at this transaction. Uses the index on invoice_lines.transaction_id. */
    public boolean isOnAnyInvoice(String id) {
        Boolean onInvoice = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM invoice_lines WHERE transaction_id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(onInvoice);
    }

    /**
     * Saves new values for an existing transaction: updates the header, then replaces all lines and details.
     * Must run inside a database transaction so readers never see a half-replaced list.
     */
    public void update(Transaction transaction) {
        jdbc.update("""
                UPDATE transactions SET occurred_at = ?, total_cents = ?, updated_at = ? WHERE id = ?
                """,
                transaction.occurredAt().toString(),
                MoneyConverter.toCents(transaction.total(), "total"),
                transaction.updatedAt().toString(),
                transaction.id());
        deleteLinesAndDetails(transaction.id());
        insertLinesAndDetails(transaction);
    }

    /**
     * Deletes the transaction. ON DELETE CASCADE removes its lines and details in the same statement.
     * Returns false if no transaction had this id.
     */
    public boolean delete(String id) {
        return jdbc.update("DELETE FROM transactions WHERE id = ?", id) == 1;
    }

    // Fixed table names written here, never taken from a request, so building the SQL text is safe.
    private static final List<String> CHILD_TABLES = List.of(
            "transaction_tax_lines", "transaction_fee_lines",
            "flight_coupons", "flight_details", "hotel_extras", "hotel_details", "rail_legs", "navan_fee_details");

    private void deleteLinesAndDetails(String id) {
        for (String table : CHILD_TABLES) {
            jdbc.update("DELETE FROM " + table + " WHERE transaction_id = ?", id);
        }
    }

    private void insertLinesAndDetails(Transaction transaction) {
        String id = transaction.id();
        List<TaxLine> taxLines = transaction.taxLines();
        for (int i = 0; i < taxLines.size(); i++) {
            TaxLine line = taxLines.get(i);
            jdbc.update("""
                    INSERT INTO transaction_tax_lines (transaction_id, position, name, rate, amount_cents)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    id, i, line.name(), line.rate() == null ? null : line.rate().toPlainString(),
                    MoneyConverter.toCents(line.amount(), "amount"));
        }
        List<FeeLine> feeLines = transaction.feeLines();
        for (int i = 0; i < feeLines.size(); i++) {
            FeeLine line = feeLines.get(i);
            jdbc.update("""
                    INSERT INTO transaction_fee_lines (transaction_id, position, name, amount_cents)
                    VALUES (?, ?, ?, ?)
                    """,
                    id, i, line.name(), MoneyConverter.toCents(line.amount(), "amount"));
        }
        switch (transaction.metadata()) {
            case FlightMetadata flight -> insertFlight(id, flight);
            case HotelMetadata hotel -> insertHotel(id, hotel);
            case RailMetadata rail -> insertRail(id, rail);
            case NavanFeeMetadata fee -> insertNavanFee(id, fee);
        }
    }

    private void insertFlight(String id, FlightMetadata flight) {
        jdbc.update("""
                INSERT INTO flight_details (transaction_id, origin, destination, fare_cents)
                VALUES (?, ?, ?, ?)
                """,
                id, flight.origin(), flight.destination(), MoneyConverter.toCents(flight.fare(), "fare"));
        List<FlightMetadata.Coupon> coupons = flight.coupons();
        for (int i = 0; i < coupons.size(); i++) {
            FlightMetadata.Coupon coupon = coupons.get(i);
            jdbc.update("""
                    INSERT INTO flight_coupons
                        (transaction_id, position, flight_number, carrier, from_airport, to_airport, departure, arrival)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, i, coupon.flightNumber(), coupon.carrier(), coupon.from(), coupon.to(),
                    coupon.departure().toString(), coupon.arrival().toString());
        }
    }

    private void insertHotel(String id, HotelMetadata hotel) {
        jdbc.update("""
                INSERT INTO hotel_details (transaction_id, name, street, city, country,
                                           check_in, check_out, nights, room_rate_per_night_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, hotel.name(), hotel.address().street(), hotel.address().city(), hotel.address().country(),
                hotel.checkIn().toString(), hotel.checkOut().toString(), hotel.nights(),
                MoneyConverter.toCents(hotel.roomRatePerNight(), "room_rate_per_night"));
        List<HotelMetadata.Extra> extras = hotel.extras();
        for (int i = 0; i < extras.size(); i++) {
            HotelMetadata.Extra extra = extras.get(i);
            jdbc.update("""
                    INSERT INTO hotel_extras (transaction_id, position, name, amount_cents)
                    VALUES (?, ?, ?, ?)
                    """,
                    id, i, extra.name(), MoneyConverter.toCents(extra.amount(), "amount"));
        }
    }

    private void insertRail(String id, RailMetadata rail) {
        List<RailMetadata.Leg> legs = rail.legs();
        for (int i = 0; i < legs.size(); i++) {
            RailMetadata.Leg leg = legs.get(i);
            jdbc.update("""
                    INSERT INTO rail_legs
                        (transaction_id, position, origin, destination, departure, arrival, train_number, travel_class)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, i, leg.origin(), leg.destination(), leg.departure().toString(), leg.arrival().toString(),
                    leg.trainNumber(), leg.travelClass());
        }
    }

    private void insertNavanFee(String id, NavanFeeMetadata fee) {
        jdbc.update("""
                INSERT INTO navan_fee_details (transaction_id, fee_kind, description, related_external_id)
                VALUES (?, ?, ?, ?)
                """,
                id, fee.feeKind().value(), fee.description(), fee.relatedExternalId());
    }
}
