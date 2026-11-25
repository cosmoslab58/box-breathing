package dev.cosmoslab.boxbreathing;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Only used when "Keep running with the screen off" is on. The timer itself stays in MainActivity;
 * this foreground service (with its required notification) and a partial wake lock just keep the
 * process and CPU awake so the ticks and cues continue with the screen off or the app in the
 * background. It runs while a session is running and stops on pause, reset or finish.
 */
public class SessionService extends Service {
    private static final String CHANNEL = "session";
    private static final long MAX_HOLD_MS = 3 * 60 * 60 * 1000L; // safety cap for open-ended sessions

    private PowerManager.WakeLock lock;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, SessionService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, SessionService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Breathing session", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Breathing session running")
                .setContentText("Tap to return to Box Breathing")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (lock == null) {
            lock = getSystemService(PowerManager.class)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boxbreathing:session");
            lock.setReferenceCounted(false);
            lock.acquire(MAX_HOLD_MS);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (lock != null && lock.isHeld()) lock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
