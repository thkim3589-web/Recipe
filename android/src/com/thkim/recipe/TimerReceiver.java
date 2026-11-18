package com.thkim.recipe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

// 요리 타이머가 끝나면 앱이 꺼져 있거나 다른 앱을 쓰는 중이어도 알림(소리·진동)을 띄움
public class TimerReceiver extends BroadcastReceiver {
    static final String CHANNEL = "cook_timer";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String label = intent.getStringExtra("label");
        String dish = intent.getStringExtra("dish");
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "요리 타이머", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("요리 모드 타이머가 끝나면 알려줘요");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 500});
            ch.setSound(sound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat_timer)
         .setContentTitle("⏰ " + (label == null ? "타이머" : label) + " 끝났어요!")
         .setContentText(dish == null || dish.isEmpty() ? "요리를 확인하세요" : dish + " — 다음 단계로 넘어가세요")
         .setCategory(Notification.CATEGORY_ALARM)
         .setAutoCancel(true)
         .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(sound).setVibrate(new long[]{0, 500, 250, 500, 250, 500}).setPriority(Notification.PRIORITY_HIGH);
        }
        nm.notify(7, b.build());
    }
}
