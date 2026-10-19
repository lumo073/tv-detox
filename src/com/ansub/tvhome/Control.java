package com.ansub.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

// Lets the laptop (adb) talk to the home screen without bringing it to the front. Only senders
// holding DUMP can reach it, which is adb and the system, never other apps (see the manifest).
//
//   am broadcast -n com.ansub.tvhome/.Control -a com.ansub.tvhome.USAGE
//       writes the last 7 days of watch time to /sdcard/Android/data/com.ansub.tvhome/files/usage.json
//   am broadcast -n com.ansub.tvhome/.Control -a com.ansub.tvhome.SET --es goal 180
//       saves settings: goal (minutes a day, 0 = off), name, bedtime, birthday, trim
public class Control extends BroadcastReceiver {
    static final String USAGE = "com.ansub.tvhome.USAGE", SET = "com.ansub.tvhome.SET";
    private static final String[] SETTABLE = {"goal", "name", "bedtime", "birthday", "trim"};
    static final int DEFAULT_GOAL = 180; // minutes

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        if (SET.equals(intent.getAction())) {
            SharedPreferences.Editor e = ctx.getSharedPreferences("config", 0).edit();
            for (String k : SETTABLE) {
                String v = intent.getStringExtra(k);
                if (v == null) continue;
                if (v.trim().isEmpty() || v.equalsIgnoreCase("none")) e.remove(k);
                else e.putString(k, v.trim());
            }
            e.apply();
            setResultData("ok");
        } else if (USAGE.equals(intent.getAction())) {
            final PendingResult done = goAsync(); // the log can take a moment: off the main thread
            new Thread(() -> {
                try {
                    writeUsage(ctx);
                    done.setResultData("ok");
                } catch (Exception ex) {
                    done.setResultData("error: " + ex);
                } finally {
                    done.finish();
                }
            }).start();
        }
    }

    static int goalMinutes(Context ctx) {
        try {
            return Integer.parseInt(ctx.getSharedPreferences("config", 0).getString("goal", "" + DEFAULT_GOAL));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // {"goal": 180, "days": [{"date": "2026-10-19", "label": "Mon", "total": ms, "apps": [{"name", "ms"}...]}]}
    private static void writeUsage(Context ctx) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        List<Map<String, Long>> days = Usage.perDay(ctx, 7);
        JSONArray list = new JSONArray();
        for (int i = 0; i < days.size(); i++) {
            long start = Usage.dayStart(days.size() - 1 - i);
            JSONArray apps = new JSONArray();
            for (Map.Entry<String, Long> a : days.get(i).entrySet()) {
                String name = a.getKey();
                try { name = pm.getApplicationLabel(pm.getApplicationInfo(a.getKey(), 0)).toString(); }
                catch (Exception ignored) {}
                apps.put(new JSONObject().put("pkg", a.getKey()).put("name", name).put("ms", a.getValue()));
            }
            list.put(new JSONObject()
                    .put("date", DateFormat.format("yyyy-MM-dd", start).toString())
                    .put("label", DateFormat.format("EEE", start).toString())
                    .put("total", Usage.total(days.get(i)))
                    .put("apps", apps));
        }
        JSONObject out = new JSONObject().put("goal", goalMinutes(ctx)).put("days", list)
                .put("made", System.currentTimeMillis());
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) throw new IllegalStateException("no storage");
        File tmp = new File(dir, "usage.json.tmp");
        try (FileOutputStream f = new FileOutputStream(tmp)) { f.write(out.toString().getBytes("UTF-8")); }
        if (!tmp.renameTo(new File(dir, "usage.json"))) throw new IllegalStateException("couldn't save");
    }
}
