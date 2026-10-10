package dev.cosmoslab.boxbreathing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The session runs on a clock anchored at t0: every phase length is whole seconds, so each
 * tick lands on a whole-second boundary from t0 and the schedule never drifts. A Handler fires
 * the audio at those boundaries; the box redraws every frame from the same clock. Pausing
 * shifts t0 forward by the time spent paused.
 */
public class MainActivity extends Activity {
    static final int BG = 0xFF0E1A22, SURFACE = 0xFF1A2B36;

    private enum State { IDLE, RUNNING, PAUSED, DONE }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Sounds sounds;
    private TextToSpeech tts;
    private boolean ttsReady;
    private Vibrator vibrator;

    private BoxView box;
    private TextView pattern, stats, start, reset, settings;

    private State state = State.IDLE;
    private int[] secs;
    private long[] durMs = new long[4];
    private long cycleMs, t0, pausedAt, lastKey = Long.MIN_VALUE;
    private int rounds; // 0 = until stopped

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // draw edge to edge on every version, as targetSdk 35 forces on Android 15; insets pad the content
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = new Prefs(this);
        sounds = new Sounds(this);
        vibrator = getSystemService(Vibrator.class);
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            tts.setLanguage(Locale.getDefault());
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            ttsReady = true;
        });
        setContentView(buildUi());
        if (!prefs.disclaimerAccepted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Before you start")
                    .setMessage(Prefs.DISCLAIMER)
                    .setCancelable(false)
                    .setPositiveButton("I understand", (d, w) -> prefs.acceptDisclaimer())
                    .show();
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        // targetSdk 35 draws edge to edge, so keep content clear of the system bars ourselves
        root.setOnApplyWindowInsetsListener((v, in) -> {
            v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                    in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            return in;
        });

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(12), dp(8), 0);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = text("Box Breathing", 22, BoxView.TEXT);
        pattern = text("", 14, BoxView.MUTED);
        titles.addView(name);
        titles.addView(pattern);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        settings = text("⚙", 26, BoxView.MUTED);
        settings.setPadding(dp(14), dp(6), dp(14), dp(6));
        settings.setContentDescription("Settings");
        settings.setOnClickListener(v -> {
            stop();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        top.addView(settings);
        root.addView(top);

        box = new BoxView(this);
        root.addView(box, new LinearLayout.LayoutParams(-1, 0, 1));

        stats = text("", 15, BoxView.MUTED);
        stats.setGravity(Gravity.CENTER);
        root.addView(stats, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(dp(20), dp(16), dp(20), dp(24));
        reset = button("Reset", false);
        reset.setOnClickListener(v -> stop());
        start = button("Start", true);
        start.setOnClickListener(v -> toggle());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1);
        buttons.addView(reset, lp);
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        buttons.addView(start, new LinearLayout.LayoutParams(0, dp(56), 2));
        root.addView(buttons);
        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state == State.IDLE || state == State.DONE) loadConfig();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // No foreground service: leaving the app pauses rather than ticking unreliably in the background.
        if (state == State.RUNNING) pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        sounds.release();
        tts.shutdown();
    }

    private void loadConfig() {
        secs = prefs.phases();
        cycleMs = 0;
        for (int i = 0; i < 4; i++) {
            durMs[i] = secs[i] * 1000L;
            cycleMs += durMs[i];
        }
        int minutes = prefs.get(Prefs.MINUTES);
        rounds = minutes == 0 ? 0 : (int) Math.ceil(minutes * 60000.0 / cycleMs);
        pattern.setText(Prefs.pattern(secs) + " seconds");
        box.setDurations(secs);
        state = State.IDLE;
        box.rest("Ready", "");
        stats.setText(rounds == 0 ? "Until you stop" : rounds + " rounds · " + clock(rounds * cycleMs));
        refreshButtons();
    }

    // ---- session control ----

    private void toggle() {
        switch (state) {
            case RUNNING -> pause();
            case PAUSED -> resume();
            default -> begin();
        }
    }

    private void begin() {
        loadConfig();
        t0 = SystemClock.uptimeMillis() + prefs.get(Prefs.LEAD) * 1000L;
        lastKey = Long.MIN_VALUE;
        state = State.RUNNING;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.post(beat);
        box.postOnAnimation(frame);
        refreshButtons();
    }

    private void pause() {
        pausedAt = SystemClock.uptimeMillis();
        state = State.PAUSED;
        handler.removeCallbacks(beat);
        tts.stop();
        stats.setText("Paused · " + stats.getText());
        refreshButtons();
    }

    private void resume() {
        t0 += SystemClock.uptimeMillis() - pausedAt;
        state = State.RUNNING;
        handler.post(beat);
        box.postOnAnimation(frame);
        refreshButtons();
    }

    private void stop() {
        handler.removeCallbacks(beat);
        tts.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        loadConfig();
    }

    private void complete() {
        state = State.DONE;
        handler.removeCallbacks(beat);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sounds.play(Sounds.GONG, prefs.gain());
        if (prefs.str(Prefs.CUE).equals("voice")) say("Well done");
        buzz(300);
        box.rest("Well done", "✓");
        stats.setText(rounds + " rounds · " + clock(rounds * cycleMs));
        refreshButtons();
    }

    private void refreshButtons() {
        start.setText(switch (state) {
            case RUNNING -> "Pause";
            case PAUSED -> "Resume";
            case DONE -> "Again";
            default -> "Start";
        });
        boolean active = state == State.RUNNING || state == State.PAUSED;
        reset.setEnabled(active);
        reset.setAlpha(active ? 1f : 0.4f);
    }

    // ---- the clock ----

    private static final class Snap {
        boolean leadIn, done;
        int leadSec, round, phase;
        long phaseElapsed, elapsed;
    }

    private Snap snap(long now) {
        Snap s = new Snap();
        long e = now - t0;
        if (e < 0) {
            s.leadIn = true;
            s.leadSec = (int) ((-e + 999) / 1000);
            return s;
        }
        s.elapsed = e;
        long n = e / cycleMs;
        if (rounds > 0 && n >= rounds) {
            s.done = true;
            return s;
        }
        long off = e % cycleMs;
        int p = 0;
        while (off >= durMs[p]) off -= durMs[p++]; // zero-length holds fall straight through
        s.round = (int) n + 1;
        s.phase = p;
        s.phaseElapsed = off;
        return s;
    }

    private final Runnable beat = new Runnable() {
        @Override
        public void run() {
            if (state != State.RUNNING) return;
            long now = SystemClock.uptimeMillis();
            Snap s = snap(now);
            if (s.done) {
                complete();
                return;
            }
            long key = s.leadIn ? -s.leadSec : ((long) s.round * 4 + s.phase) * 1000 + s.phaseElapsed / 1000;
            if (key != lastKey) {
                lastKey = key;
                announce(s);
            }
            // a few ms past the next whole second, so that second is the one we compute
            handler.postAtTime(this, t0 + (Math.floorDiv(now - t0, 1000) + 1) * 1000 + 5);
        }
    };

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (state != State.RUNNING) return;
            Snap s = snap(SystemClock.uptimeMillis());
            if (s.done) return; // beat finishes the session
            if (s.leadIn) {
                box.rest("Get ready", String.valueOf(s.leadSec));
                stats.setText(rounds == 0 ? "Round 1" : "Round 1 of " + rounds);
            } else {
                int sec = (int) (s.phaseElapsed / 1000);
                box.show(s.phase, (float) s.phaseElapsed / durMs[s.phase],
                        Prefs.PHASE_NAMES[s.phase], String.valueOf(shown(s.phase, sec)));
                stats.setText((rounds == 0 ? "Round " + s.round : "Round " + s.round + " of " + rounds)
                        + " · " + clock(s.elapsed) + (rounds == 0 ? "" : " / " + clock(rounds * cycleMs)));
            }
            box.postOnAnimation(this);
        }
    };

    private int shown(int phase, int sec) {
        return prefs.str(Prefs.COUNT).equals("down") ? secs[phase] - sec : sec + 1;
    }

    // ---- sound ----

    private void announce(Snap s) {
        if (s.leadIn) {
            second(s.leadSec, false);
            return;
        }
        int sec = (int) (s.phaseElapsed / 1000);
        int n = shown(s.phase, sec);
        if (sec > 0) {
            second(n, false);
            return;
        }
        if (prefs.vibrate()) buzz(70);
        switch (prefs.str(Prefs.CUE)) {
            case "voice" -> say(Prefs.PHASE_NAMES[s.phase]);
            case "chime" -> {
                sounds.play(s.phase == 0 ? Sounds.CHIME_IN : s.phase == 2 ? Sounds.CHIME_OUT : Sounds.CHIME_HOLD,
                        prefs.gain());
                second(n, true);
            }
            default -> second(n, true);
        }
    }

    /** The every-second sound; accent marks the first second of a phase. */
    private void second(int n, boolean accent) {
        float g = prefs.gain();
        switch (prefs.str(Prefs.SECOND)) {
            case "tick" -> sounds.play(accent ? Sounds.TICK_ACCENT : Sounds.TICK, g);
            case "beep" -> sounds.play(accent ? Sounds.BEEP_ACCENT : Sounds.BEEP, g);
            case "voice" -> say(String.valueOf(n));
            default -> { }
        }
    }

    private void say(String words) {
        if (!ttsReady) return;
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0.05f, prefs.gain()));
        tts.speak(words, TextToSpeech.QUEUE_FLUSH, b, "box");
    }

    private void buzz(long ms) {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    // ---- helpers ----

    private static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String s, boolean primary) {
        TextView b = text(s, 17, primary ? BG : BoxView.TEXT);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        bg.setColor(primary ? BoxView.ACCENT : SURFACE);
        b.setBackground(bg);
        b.setClickable(true);
        return b;
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
