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
