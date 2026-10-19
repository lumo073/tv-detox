package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.File;
import java.util.List;

// Screensaver: the wallpapers fade from one to the next with a small clock, like a photo frame.
// Two screen-sized bitmaps at most (the one showing and the one fading in). The TV still goes to
// sleep at its usual time. Turn it on with:
//   settings put secure screensaver_components com.ansub.tvhome/.PhotoFrame
//   settings put secure screensaver_enabled 1
public class PhotoFrame extends DreamService {
    private static final long EVERY = 30_000, FADE = 1500;

    private final Handler handler = new Handler();
    private ImageView front, back;
    private List<File> files;
    private int index;
    private int w, h;

    private final Runnable next = new Runnable() {
        @Override public void run() {
            show(index + 1, true);
            handler.postDelayed(this, EVERY);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // any key wakes it
        setFullscreen(true);
        w = getResources().getDisplayMetrics().widthPixels;
        h = getResources().getDisplayMetrics().heightPixels;
        float scale = w / 1920f;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        back = new ImageView(this);
        front = new ImageView(this);
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        root.addView(front, new FrameLayout.LayoutParams(-1, -1));

        // A soft shade in the corner, so the clock reads on bright pictures.
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.BR_TL,
                new int[] {0x99000000, 0x00000000, 0x00000000});
        FrameLayout shadeView = new FrameLayout(this);
        shadeView.setBackground(shade);
        root.addView(shadeView, new FrameLayout.LayoutParams(-1, -1));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(0xE6FFFFFF);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(96 * scale));
        clock.setShadowLayer(12 * scale, 0, 0, 0x80000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cp.setMargins(0, 0, Math.round(96 * scale), Math.round(72 * scale));
        root.addView(clock, cp);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        files = Wallpapers.list(this);
        if (files.isEmpty()) return; // just the clock on black
        // Start from today's home screen wallpaper's neighbour, so it isn't the same picture.
        index = (int) (Wallpapers.today() % files.size());
        show(index + 1, false);
        if (files.size() > 1) handler.postDelayed(next, EVERY);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacksAndMessages(null);
        front.setImageDrawable(null);
        back.setImageDrawable(null);
        super.onDreamingStopped();
    }

    private void show(int i, boolean fade) {
        index = ((i % files.size()) + files.size()) % files.size();
        Bitmap b = Wallpapers.load(files.get(index), w, h);
        if (b == null) return;
        // The old picture moves behind, the new one fades in on top, then the old one is let go.
        back.setImageDrawable(front.getDrawable());
        front.setImageBitmap(b);
        if (!fade) return;
        front.setAlpha(0f);
        front.animate().alpha(1f).setDuration(FADE).withEndAction(() -> back.setImageDrawable(null)).start();
    }
}
