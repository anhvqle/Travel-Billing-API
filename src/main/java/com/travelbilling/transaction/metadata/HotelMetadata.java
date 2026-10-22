package com.travelbilling.transaction.metadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.shared.Validation;
import com.travelbilling.shared.ValidationException;
import com.travelbilling.transaction.model.TransactionType;

/** A hotel stay: the property's name and address, the dates, and the nightly rate. Extras are optional. */
public record HotelMetadata(String name, Address address, LocalDate checkIn, LocalDate checkOut,
                            Integer nights, BigDecimal roomRatePerNight, List<Extra> extras)
        implements TransactionMetadata {

    /** The property's address. Street, city and country are required. */
    public record Address(String street, String city, String country) {
    }

    /** Something added to the stay, e.g. breakfast. */
    public record Extra(String name, BigDecimal amount) {
    }

    public HotelMetadata {
        if (extras == null) {
            extras = List.of();
        }
    }

    @Override
    public TransactionType type() {
        return TransactionType.HOTEL;
    }

    @Override
    public void validate() {
        Validation.requireText(name, "metadata.name");
        Validation.requirePresent(address, "metadata.address");
        Validation.requireText(address.street(), "metadata.address.street");
        Validation.requireText(address.city(), "metadata.address.city");
        Validation.requireText(address.country(), "metadata.address.country");
        Validation.requirePresent(checkIn, "metadata.check_in");
        Validation.requirePresent(checkOut, "metadata.check_out");
        if (!checkOut.isAfter(checkIn)) {
            throw new ValidationException("metadata.check_out must be after check_in");
        }
        Validation.requirePresent(nights, "metadata.nights");
        long nightsBetweenDates = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights != nightsBetweenDates) {
            throw new ValidationException("metadata.nights is " + nights + " but check_in to check_out is "
                    + nightsBetweenDates + " nights");
        }
        MoneyConverter.toCents(roomRatePerNight, "metadata.room_rate_per_night");
        for (int i = 0; i < extras.size(); i++) {
            String field = "metadata.extras[" + i + "]";
            Extra extra = Validation.requirePresent(extras.get(i), field);
            Validation.requireText(extra.name(), field + ".name");
            MoneyConverter.toCents(extra.amount(), field + ".amount");
        }
    }
}
