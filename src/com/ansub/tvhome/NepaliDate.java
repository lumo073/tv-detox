package com.ansub.tvhome;

import java.util.Calendar;

// Today's date in Bikram Sambat, Nepal's calendar, shown in Devanagari (like "2 Kartik 2083").
// BS months have no formula, so the lengths come from the published calendar (via the
// nepali-datetime project's table, checked against known New Year dates). Covers BS 2080 to 2100
// (April 2023 to 2044); outside that, of() returns null and nothing is shown.
final class NepaliDate {
    private NepaliDate() {}

    // Days in each month, Baisakh to Chait.
    private static final int FIRST_YEAR = 2080;
    private static final int[][] DAYS = {
            {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 2080
            {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2081
            {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2082
            {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2083
            {31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30}, // 2084
            {31, 32, 31, 32, 30, 31, 30, 30, 29, 30, 30, 30}, // 2085
            {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2086
            {31, 31, 32, 31, 31, 31, 30, 29, 30, 30, 30, 30}, // 2087
            {30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30}, // 2088
            {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2089
            {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2090
            {31, 31, 32, 31, 31, 31, 30, 30, 29, 30, 30, 30}, // 2091
            {30, 31, 32, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2092
            {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2093
            {31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30}, // 2094
            {31, 31, 32, 31, 31, 31, 30, 29, 30, 30, 30, 30}, // 2095
            {30, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2096
            {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2097
            {31, 31, 32, 31, 31, 31, 29, 30, 29, 30, 29, 31}, // 2098
            {31, 31, 32, 31, 31, 31, 30, 29, 29, 30, 30, 30}, // 2099
            {31, 32, 31, 32, 30, 31, 30, 29, 30, 29, 30, 30}, // 2100
    };
    private static final String[] MONTHS = {"\u092c\u0948\u0936\u093e\u0916", "\u091c\u0947\u0920", "\u0905\u0938\u093e\u0930", "\u0938\u093e\u0909\u0928", "\u092d\u0926\u094c", "\u0905\u0938\u094b\u091c", "\u0915\u093e\u0930\u094d\u0924\u093f\u0915", "\u092e\u0902\u0938\u093f\u0930", "\u092a\u0941\u0938", "\u092e\u093e\u0918", "\u092b\u093e\u0917\u0941\u0928", "\u091a\u0948\u0924"};
    private static final String DIGITS = "\u0966\u0967\u0968\u0969\u096a\u096b\u096c\u096d\u096e\u096f";

    // {year, month 1-12, day} for a local date, or null when out of range.
    static int[] of(Calendar c) {
        // Days since BS 2080 Baisakh 1, which was 14 April 2023.
        Calendar start = Calendar.getInstance();
        start.clear();
        start.set(2023, Calendar.APRIL, 14);
        Calendar day = Calendar.getInstance();
        day.clear();
        day.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        // Rounding keeps daylight-saving hours out of the day count.
        long n = Math.round((day.getTimeInMillis() - start.getTimeInMillis()) / 86400000.0);
        if (n < 0) return null;
        for (int y = 0; y < DAYS.length; y++)
            for (int m = 0; m < 12; m++) {
                if (n < DAYS[y][m]) return new int[] {FIRST_YEAR + y, m + 1, (int) n + 1};
                n -= DAYS[y][m];
            }
        return null;
    }

    // Day, month and year in Devanagari, or null.
    static String format(int[] d) {
        return d == null ? null : digits(d[2]) + " " + MONTHS[d[1] - 1] + " " + digits(d[0]);
    }

    static String digits(int n) {
        StringBuilder b = new StringBuilder();
        for (char ch : String.valueOf(n).toCharArray()) b.append(DIGITS.charAt(ch - '0'));
        return b.toString();
    }
}
