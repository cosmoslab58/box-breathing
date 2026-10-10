package dev.cosmoslab.boxbreathing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/**
 * The box: a dot travels clockwise from the bottom-left corner, one side per phase
 * (up = in, across the top = hold, down = out, along the bottom = hold), leaving a trail
 * for the current round. An inner square swells on the in-breath and shrinks on the out-breath.
 */
final class BoxView extends View {
    static final int ACCENT = 0xFF6FD3C1, TEXT = 0xFFE8F1F2, MUTED = 0xFF8AA1AA, TRACK = 0x33FFFFFF;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int[] durations = {4, 4, 4, 4};
    private int phase = -1;          // -1 = not in a breathing phase (idle, lead-in, done)
    private float fraction;          // 0..1 through the current phase
    private String title = "Ready", big = "";

    BoxView(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(dp(6));
        track.setColor(TRACK);
        track.setStrokeJoin(Paint.Join.ROUND);
        trail.set(track);
        trail.setColor(ACCENT);
        trail.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(0xFFFFFFFF);
        glow.setColor(ACCENT);
        glow.setAlpha(80);
        fill.setColor(ACCENT);
        fill.setAlpha(36);
        label.setColor(TEXT);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(sp(24));
        number.setColor(TEXT);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTextSize(sp(88));
        number.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        side.setTextAlign(Paint.Align.CENTER);
        side.setTextSize(sp(13));
        side.setLetterSpacing(0.12f);
        side.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    void setDurations(int[] d) {
        durations = d.clone();
        invalidate();
    }

    /** A breathing phase is in progress. */
    void show(int phase, float fraction, String title, String big) {
        this.phase = phase;
        this.fraction = fraction;
        this.title = title;
        this.big = big;
        invalidate();
    }

    /** Idle, lead-in or done: the dot rests at the start. */
    void rest(String title, String big) {
        show(-1, 0, title, big);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h) - 2 * dp(40);
        float l = (w - size) / 2, t = (h - size) / 2, r = l + size, b = t + size;
        float cx = w / 2, cy = h / 2;
        // corners in travel order: bottom-left, top-left, top-right, bottom-right
        float[][] corner = {{l, b}, {l, t}, {r, t}, {r, b}};

        // inner square: the breath itself
        float lo = 0.30f, hi = 0.86f, e = ease(fraction);
        float scale = switch (phase) {
            case 0 -> lo + (hi - lo) * e;
            case 1 -> hi;
            case 2 -> hi - (hi - lo) * e;
            default -> lo;
        };
        float half = size * scale / 2;
        rect.set(cx - half, cy - half, cx + half, cy + half);
        c.drawRoundRect(rect, dp(18), dp(18), fill);

        c.drawRect(l, t, r, b, track);

        float px = l, py = b;
        if (phase >= 0) {
            path.reset();
            path.moveTo(l, b);
            for (int i = 0; i < phase; i++) path.lineTo(corner[(i + 1) % 4][0], corner[(i + 1) % 4][1]);
            float[] from = corner[phase], to = corner[(phase + 1) % 4];
            px = from[0] + (to[0] - from[0]) * fraction;
            py = from[1] + (to[1] - from[1]) * fraction;
            path.lineTo(px, py);
            c.drawPath(path, trail);
        }
        c.drawCircle(px, py, dp(16), glow);
        c.drawCircle(px, py, dp(8), dot);

        // side labels, rotated to run along their side
        float gap = dp(18);
        float[][] at = {{l - gap, cy, -90}, {cx, t - gap + dp(5), 0}, {r + gap, cy, 90}, {cx, b + gap + dp(5), 0}};
        for (int i = 0; i < 4; i++) {
            side.setColor(i == phase ? ACCENT : durations[i] == 0 ? 0x40FFFFFF : MUTED);
            c.save();
            c.rotate(at[i][2], at[i][0], at[i][1]);
            float base = at[i][2] == 0 ? at[i][1] : at[i][1] + dp(5);
            c.drawText(Prefs.SIDE_LABELS[i] + "  " + durations[i], at[i][0], base, side);
            c.restore();
        }

        c.drawText(title, cx, cy - dp(44), label);
        c.drawText(big, cx, cy + dp(52), number);
    }

    private static float ease(float f) {
        return (float) (1 - Math.cos(Math.PI * f)) / 2;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
