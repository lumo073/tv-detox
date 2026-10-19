package com.ansub.tvhome;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Watch time from Android's own usage log: how long each app was on screen, per local day.
// The home screen itself doesn't count. Needs usage access:
//   adb shell appops set com.ansub.tvhome GET_USAGE_STATS allow
final class Usage {
    private Usage() {}

    // Midnight at the start of the day `back` days ago (0 = today), local time.
    static long dayStart(int back) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -back);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // For each of the last `days` days, oldest first: package -> milliseconds on screen.
    // An app on screen right now counts up to now; other unfinished stretches (say, the TV lost
    // power) are dropped rather than guessed.
    static List<Map<String, Long>> perDay(Context ctx, int days) {
        List<Map<String, Long>> out = new ArrayList<>();
        long[] starts = new long[days + 1];
        for (int i = 0; i < days; i++) {
            out.add(new HashMap<String, Long>());
            starts[i] = dayStart(days - 1 - i);
        }
        long now = System.currentTimeMillis();
        starts[days] = Long.MAX_VALUE;
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents events = usm.queryEvents(starts[0], now);
            UsageEvents.Event ev = new UsageEvents.Event();
            Map<String, Long> since = new HashMap<>();
            String last = null; // the app that came to the front most recently
            while (events.getNextEvent(ev)) {
                String p = ev.getPackageName();
                // Not the home screen, and not System UI (pop-ups and volume, not watching).
                if (p == null || p.equals(ctx.getPackageName()) || p.equals("com.android.systemui")) continue;
                if (ev.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    since.put(p, ev.getTimeStamp());
                    last = p;
                } else if (ev.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    Long from = since.remove(p);
                    if (from != null) add(out, starts, p, from, ev.getTimeStamp());
                    if (p.equals(last)) last = null;
                }
            }
            if (last != null && since.containsKey(last)) add(out, starts, last, since.get(last), now);
        } catch (Exception ignored) {
            // No usage access, or the log can't be read: no time rather than a wrong time.
        }
        return out;
    }

    // Adds [from, to) to app p, split at midnights.
    private static void add(List<Map<String, Long>> out, long[] starts, String p, long from, long to) {
        for (int i = 0; i < out.size(); i++) {
            long a = Math.max(from, starts[i]), b = Math.min(to, starts[i + 1]);
            if (b > a) {
                Long had = out.get(i).get(p);
                out.get(i).put(p, (had == null ? 0 : had) + (b - a));
            }
        }
    }

    static long total(Map<String, Long> day) {
        long t = 0;
        for (long v : day.values()) t += v;
        return t;
    }
}
