package com.travelbilling.shared;

import java.util.List;
import java.util.function.Function;

/** Limit rules for every list endpoint: default 50, max 100. Over 100 is refused (400), not silently capped. */
public final class Pagination {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private Pagination() {
    }

    public static int validLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ValidationException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        return limit;
    }

    /**
     * Builds a page from a query that fetched limit + 1 rows: the extra row only tells us a next page exists.
     * idOf says which value of the last item becomes the next cursor.
     */
    public static <T> Page<T> toPage(List<T> rowsPlusOne, int limit, Function<T, String> idOf) {
        boolean hasMore = rowsPlusOne.size() > limit;
        List<T> items = hasMore ? rowsPlusOne.subList(0, limit) : rowsPlusOne;
        String nextCursor = hasMore ? idOf.apply(items.get(items.size() - 1)) : null;
        return new Page<>(items, nextCursor, limit);
    }
}
