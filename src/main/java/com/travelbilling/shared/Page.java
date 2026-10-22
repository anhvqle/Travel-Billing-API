package com.travelbilling.shared;

import java.util.List;

/**
 * One page of a list. next_cursor is the value to send as ?cursor= to get the next page;
 * it is absent on the last page.
 */
public record Page<T>(List<T> items, String nextCursor, int limit) {
}
