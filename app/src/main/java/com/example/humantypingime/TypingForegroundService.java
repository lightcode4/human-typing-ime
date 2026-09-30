/*
 * TypingForegroundService.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: FIX(i) — add deterministic teardown: onDestroy() removes the
 *             foreground notification and onTaskRemoved() stops the service, so
 *             the foreground state never outlives the typing job. (This service
 *             owns no executor/worker threads — typing runs in the IME — so there
 *             is no ExecutorService to double-shutdown; stopForeground is itself
 *             idempotent.)
 */

package com.example.humantypingime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class TypingForegroundService extends Service {

    public static final String CHANNEL_ID = "human_typing_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_STOP_TYPING = "com.example.humantypingime.STOP_TYPING";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_TYPING.equals(intent.getAction())) {
            // Broadcast to the IME to stop typing
            Intent broadcast = new Intent(ACTION_STOP_TYPING);
            broadcast.setPackage(getPackageName());
            sendBroadcast(broadcast);
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent stopIntent = new Intent(this, TypingForegroundService.class);
        stopIntent.setAction(ACTION_STOP_TYPING);
        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Human Typing in progress")
                .setContentText("Tap to stop typing")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Human Typing",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps typing alive for long texts");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
//（注：内容由AI生成）
