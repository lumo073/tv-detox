package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

// Wallpapers are plain image files pushed to the app's own folder on the TV, so no storage permission:
//   adb push 1.jpg /sdcard/Android/data/com.ansub.tvhome/files/wallpapers/
// (personalize.sh shrinks them to the screen size first.) Only one is in memory at a time, drawn
// once into a screen-sized RGB_565 bitmap: about 1.8 MB on a 720p TV, and no work after that.
final class Wallpapers {
    private Wallpapers() {}

    static List<File> list(Context c) {
        List<File> out = new ArrayList<>();
        File dir = c.getExternalFilesDir("wallpapers");
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")))
                out.add(f);
        }
        return out;
    }

    // Days since 1970 in the TV's time zone, so "daily" changes at local midnight.
    static long today() {
        long now = System.currentTimeMillis();
        return (now + TimeZone.getDefault().getOffset(now)) / 86400000L;
    }

    static String label(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // Decodes f center-cropped to exactly w x h. Big files are subsampled while decoding, so even a
    // 4K picture never needs more than a few MB on the way in. Returns null if it can't be read.
    static Bitmap load(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) return null;
            if (src.getWidth() == w && src.getHeight() == h) return src;
            // Center crop to the screen's shape, then scale.
            float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            int cw = Math.round(w / s), ch = Math.round(h / s);
            int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            new Canvas(out).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h), p);
            src.recycle();
            return out;
        } catch (Throwable t) { // OutOfMemoryError included: fall back to the plain background
            return null;
        }
    }
}
