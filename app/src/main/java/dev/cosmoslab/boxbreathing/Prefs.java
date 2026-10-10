package dev.cosmoslab.boxbreathing;

import android.content.Context;
import android.content.SharedPreferences;

/** Every user setting, with its default, in one place. */
final class Prefs {
    static final String IN = "in", HOLD_IN = "hold_in", OUT = "out", HOLD_OUT = "hold_out";
    static final String[] PHASE_KEYS = {IN, HOLD_IN, OUT, HOLD_OUT};
    static final String[] PHASE_NAMES = {"Breathe in", "Hold", "Breathe out", "Hold"};
    static final String[] SIDE_LABELS = {"IN", "HOLD", "OUT", "HOLD"};

    static final String MINUTES = "minutes", LEAD = "lead", VOLUME = "volume";
    static final String COUNT = "count", SECOND = "second", CUE = "cue", VIBRATE = "vibrate";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("box", Context.MODE_PRIVATE);
    }

    int get(String k) {
        return sp.getInt(k, switch (k) {
            case MINUTES -> 5;
            case LEAD -> 3;
            case VOLUME -> 70;
            default -> 4;
        });
    }

    String str(String k) {
        return sp.getString(k, switch (k) {
            case COUNT -> "up";
            case SECOND -> "tick";
            case CUE -> "chime";
            default -> "";
        });
    }

    boolean vibrate() {
        return sp.getBoolean(VIBRATE, true);
    }

    void put(String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    void put(String k, String v) {
        sp.edit().putString(k, v).apply();
    }

    void putVibrate(boolean v) {
        sp.edit().putBoolean(VIBRATE, v).apply();
    }

    static final String DISCLAIMER = "Box Breathing is a breathing timer for general relaxation. "
            + "It is not medical advice, diagnosis or treatment, and is no substitute for a doctor.\n\n"
            + "Holding your breath can cause dizziness, lightheadedness or fainting. Breathe gently, "
            + "stop at once if you feel unwell, and never use it while driving, operating machinery, "
            + "swimming or anywhere fainting could be dangerous. If you are pregnant or have a heart, "
            + "lung, blood pressure, seizure or other medical condition, ask a doctor first.\n\n"
            + "You use this app at your own risk. It is provided as is, without warranty of any kind, "
            + "and its author accepts no liability for any injury, loss or harm arising from its use.";

    boolean disclaimerAccepted() {
        return sp.getBoolean("disclaimer", false);
    }

    void acceptDisclaimer() {
        sp.edit().putBoolean("disclaimer", true).apply();
    }

    /** Seconds for in, hold, out, hold. */
    int[] phases() {
        int[] p = new int[4];
        for (int i = 0; i < 4; i++) p[i] = get(PHASE_KEYS[i]);
        return p;
    }

    /** Volume as a perceptual 0..1 gain, so the slider's middle sounds like the middle. */
    float gain() {
        return (float) Math.pow(get(VOLUME) / 100.0, 1.6);
    }

    static String pattern(int[] p) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) b.append(i == 0 ? "" : " · ").append(p[i]);
        return b.toString();
    }
}
