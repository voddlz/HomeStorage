package com.leekwater.homestorage.client;

/** Shortens huge counts so they fit on a slot: 1234 -> "1.2K", 15_000_000 -> "15M". */
final class CountFormat {
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private CountFormat() {}

    static String abbreviate(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }
        double value = count;
        int suffix = 0;
        while (value >= 1000 && suffix < SUFFIXES.length - 1) {
            value /= 1000;
            suffix++;
        }
        // one decimal below 10 ("1.2K"), none above, so the text never gets wider than 4 characters
        String number = value < 10 ? String.format("%.1f", value) : Long.toString((long) value);
        return number + SUFFIXES[suffix];
    }
}
