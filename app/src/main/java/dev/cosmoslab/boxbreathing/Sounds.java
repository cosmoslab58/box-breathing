package dev.cosmoslab.boxbreathing;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * All sounds are synthesized at startup and written to the cache as WAVs for SoundPool,
 * so the APK carries no audio assets and the tones can be tuned here.
 */
final class Sounds {
    static final int TICK = 0, TICK_ACCENT = 1, BEEP = 2, BEEP_ACCENT = 3;
    static final int CHIME_IN = 4, CHIME_HOLD = 5, CHIME_OUT = 6, GONG = 7;
    private static final int RATE = 44100;

    private final SoundPool pool;
    private final int[] ids = new int[8];

    Sounds(Context c) {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(aa).build();
        File dir = new File(c.getCacheDir(), "sounds");
        dir.mkdirs();
        ids[TICK] = load(dir, "tick", click(1100));
        ids[TICK_ACCENT] = load(dir, "tick_accent", click(1650));
        ids[BEEP] = load(dir, "beep", beep(660));
        ids[BEEP_ACCENT] = load(dir, "beep_accent", beep(990));
        ids[CHIME_IN] = load(dir, "chime_in", bell(784, 1.4));   // G5: rising on the in-breath
        ids[CHIME_HOLD] = load(dir, "chime_hold", bell(659, 1.4)); // E5
        ids[CHIME_OUT] = load(dir, "chime_out", bell(523, 1.4));  // C5: falling on the out-breath
        ids[GONG] = load(dir, "gong", bell(392, 3.5));
    }

    void play(int sound, float gain) {
        if (gain > 0) pool.play(ids[sound], gain, gain, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }

    /** A short woodblock-ish click: two inharmonic partials with a ~12 ms decay. */
    private static short[] click(double f) {
        int n = (int) (RATE * 0.08);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double env = Math.min(1, t / 0.001) * Math.exp(-t / 0.012);
            double v = 0.7 * Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(2 * Math.PI * f * 2.31 * t);
            s[i] = (short) (v * env * 0.9 * Short.MAX_VALUE);
        }
        return s;
    }

    /** A soft sine beep with smooth edges so it doesn't pop. */
    private static short[] beep(double f) {
        double len = 0.11;
        int n = (int) (RATE * len);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double env = Math.min(1, t / 0.01) * Math.min(1, (len - t) / 0.04);
            s[i] = (short) (Math.sin(2 * Math.PI * f * t) * env * 0.6 * Short.MAX_VALUE);
        }
        return s;
    }

    /** A bell: bell-like partial ratios, each decaying faster the higher it is. */
    private static short[] bell(double f, double len) {
        double[] ratio = {1, 2, 2.76, 5.4};
        double[] amp = {1, 0.45, 0.3, 0.12};
        double[] decay = {len * 0.4, len * 0.25, len * 0.15, len * 0.07};
        int n = (int) (RATE * len);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double v = 0;
            for (int k = 0; k < ratio.length; k++)
                v += amp[k] * Math.exp(-t / decay[k]) * Math.sin(2 * Math.PI * f * ratio[k] * t);
            double edge = Math.min(1, t / 0.003) * Math.min(1, (len - t) / 0.05);
            s[i] = (short) (v / 1.87 * edge * 0.8 * Short.MAX_VALUE);
        }
        return s;
    }

    private int load(File dir, String name, short[] pcm) {
        File f = new File(dir, name + ".wav");
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + pcm.length * 2).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(pcm.length * 2);
        for (short v : pcm) b.putShort(v);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(b.array());
        } catch (IOException e) {
            return 0;
        }
        return pool.load(f.getPath(), 1);
    }
}
