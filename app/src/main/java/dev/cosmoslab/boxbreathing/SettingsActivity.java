package dev.cosmoslab.boxbreathing;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** Every change saves immediately; the main screen reloads on return. */
public class SettingsActivity extends Activity {
    private static final int[][] PRESETS = {{4, 4, 4, 4}, {6, 6, 6, 6}, {4, 7, 8, 0}, {5, 0, 5, 0}};
    private static final String[] PRESET_NAMES = {"Box", "Long box", "4-7-8", "Coherent"};

    private Prefs prefs;
    private Sounds sounds;
    private final List<Runnable> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // draw edge to edge on every version, as targetSdk 35 forces on Android 15; insets pad the content
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = new Prefs(this);
        sounds = new Sounds(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(MainActivity.BG);
        scroll.setOnApplyWindowInsetsListener((v, in) -> {
            v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                    in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            return in;
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(32));
        scroll.addView(col);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, BoxView.TEXT);
        back.setPadding(0, 0, dp(16), dp(4));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        top.addView(text("Settings", 22, BoxView.TEXT));
        col.addView(top);

        heading(col, "Pattern");
        LinearLayout presets = new LinearLayout(this);
        for (int i = 0; i < PRESETS.length; i++) {
            int[] p = PRESETS[i];
            TextView chip = chip(PRESET_NAMES[i] + "\n" + Prefs.pattern(p).replace(" · ", "·"));
            chip.setOnClickListener(v -> {
                for (int k = 0; k < 4; k++) prefs.put(Prefs.PHASE_KEYS[k], p[k]);
                refresh();
            });
            refreshers.add(() -> {
                boolean on = java.util.Arrays.equals(prefs.phases(), p);
                select(chip, on);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.leftMargin = dp(8);
            presets.addView(chip, lp);
        }
        col.addView(presets);
        IntFunction<String> secs = v -> v + " s";
        IntFunction<String> holdSecs = v -> v == 0 ? "Skip" : v + " s";
        stepper(col, "Breathe in", Prefs.IN, 1, 30, secs);
        stepper(col, "Hold (full)", Prefs.HOLD_IN, 0, 30, holdSecs);
        stepper(col, "Breathe out", Prefs.OUT, 1, 30, secs);
        stepper(col, "Hold (empty)", Prefs.HOLD_OUT, 0, 30, holdSecs);

        heading(col, "Session");
        stepper(col, "Length", Prefs.MINUTES, 0, 60, v -> v == 0 ? "Open" : v + " min");
        stepper(col, "Get-ready countdown", Prefs.LEAD, 0, 10, v -> v == 0 ? "Off" : v + " s");
        segmented(col, "Count", Prefs.COUNT, new String[]{"up", "down"}, new String[]{"1 → 4", "4 → 1"});

        heading(col, "Sound");
        segmented(col, "Every second", Prefs.SECOND,
                new String[]{"tick", "beep", "voice", "off"}, new String[]{"Tick", "Beep", "Voice", "Off"});
        segmented(col, "Phase change", Prefs.CUE,
                new String[]{"chime", "voice", "off"}, new String[]{"Chime", "Voice", "Off"});
        volume(col);
        Switch vib = new Switch(this);
        vib.setText("Vibrate on phase change");
        vib.setTextColor(BoxView.TEXT);
        vib.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        vib.setChecked(prefs.vibrate());
        vib.setOnCheckedChangeListener((b, on) -> prefs.putVibrate(on));
        vib.setPadding(0, dp(14), 0, dp(14));
        col.addView(vib);

        TextView note = text("The phone volume buttons set the overall level; the slider sets it "
                + "relative to other media. The screen stays on during a session.", 13, BoxView.MUTED);
        note.setPadding(0, dp(8), 0, 0);
        col.addView(note);

        heading(col, "Safety");
        TextView safety = text(Prefs.DISCLAIMER, 13, BoxView.MUTED);
        safety.setLineSpacing(0, 1.15f);
        col.addView(safety);

        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sounds.release();
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private void heading(LinearLayout col, String s) {
        TextView h = text(s.toUpperCase(java.util.Locale.ROOT), 12, BoxView.ACCENT);
        h.setLetterSpacing(0.12f);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        h.setPadding(0, dp(28), 0, dp(10));
        col.addView(h);
    }

    private LinearLayout row(LinearLayout col, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.addView(text(title, 16, BoxView.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(row);
        return row;
    }

    private void stepper(LinearLayout col, String title, String key, int min, int max, IntFunction<String> fmt) {
        LinearLayout row = row(col, title);
        TextView minus = round("−"), plus = round("+");
        TextView value = text("", 17, BoxView.TEXT);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(72));
        minus.setOnClickListener(v -> {
            prefs.put(key, Math.max(min, prefs.get(key) - 1));
            refresh();
        });
        plus.setOnClickListener(v -> {
            prefs.put(key, Math.min(max, prefs.get(key) + 1));
            refresh();
        });
        refreshers.add(() -> {
            int v = prefs.get(key);
            value.setText(fmt.apply(v));
            minus.setAlpha(v > min ? 1f : 0.3f);
            plus.setAlpha(v < max ? 1f : 0.3f);
        });
        row.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(value);
        row.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
    }

    private void segmented(LinearLayout col, String title, String key, String[] values, String[] labels) {
        TextView t = text(title, 16, BoxView.TEXT);
        t.setPadding(0, dp(12), 0, dp(8));
        col.addView(t);
        LinearLayout seg = new LinearLayout(this);
        for (int i = 0; i < values.length; i++) {
            String val = values[i];
            TextView c = chip(labels[i]);
            c.setOnClickListener(v -> {
                prefs.put(key, val);
                refresh();
                if (key.equals(Prefs.SECOND)) preview();
            });
            refreshers.add(() -> select(c, prefs.str(key).equals(val)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) lp.leftMargin = dp(8);
            seg.addView(c, lp);
        }
        col.addView(seg);
    }

    private void volume(LinearLayout col) {
        LinearLayout row = row(col, "Volume");
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(prefs.get(Prefs.VOLUME));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) prefs.put(Prefs.VOLUME, v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                preview();
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, -2, 1.6f));
    }

    private void preview() {
        sounds.play(prefs.str(Prefs.SECOND).equals("beep") ? Sounds.BEEP_ACCENT : Sounds.TICK_ACCENT, prefs.gain());
    }

    private TextView chip(String s) {
        TextView c = text(s, 14, BoxView.TEXT);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(4), dp(10), dp(4), dp(10));
        c.setClickable(true);
        return c;
    }

    private void select(TextView c, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(on ? BoxView.ACCENT : MainActivity.SURFACE);
        c.setBackground(bg);
        c.setTextColor(on ? MainActivity.BG : BoxView.TEXT);
    }

    private TextView round(String s) {
        TextView b = text(s, 22, BoxView.TEXT);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(MainActivity.SURFACE);
        b.setBackground(bg);
        b.setClickable(true);
        return b;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
