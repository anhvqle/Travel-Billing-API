package com.travelbilling.shared;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Server ids like "txn_019a0f3c2b1d7e4f8a9b6c5d4e3f2a10": the creation time in milliseconds (hex),
 * then 64 random bits. Ids therefore sort by creation time, which lets list pagination use ORDER BY id.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String newId(String prefix) {
        return prefix + "_"
                + String.format("%012x", System.currentTimeMillis())
                + String.format("%016x", ThreadLocalRandom.current().nextLong());
    }
}
