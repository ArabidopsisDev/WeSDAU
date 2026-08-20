package com.sdau.campuskit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object CourseNotification {
    const val CHANNEL_ID = "course_reminders"
    private const val NOTIFICATION_ID = 4101

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel("course_reminders_silent_v2")
        manager.deleteNotificationChannel("course_reminders_silent_v3")
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null && (existing.sound != null || existing.shouldVibrate())) {
            manager.deleteNotificationChannel(CHANNEL_ID)
        }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "课程提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "下一节课程提醒（默认静音）"
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, name: String, room: String, time: String) {
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            4103,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val normalizedRoom = room.replace(Regex("\\s+"), "")
        val text = "${name}丨@${normalizedRoom}丨$time"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logout)
            .setContentTitle("WeSDAU课程表")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setSubText("课程提醒")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val room = intent.getStringExtra(EXTRA_ROOM) ?: ""
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""
        CourseNotification.show(context, name, room, time)
    }

    companion object {
        const val EXTRA_NAME = "course_name"
        const val EXTRA_ROOM = "course_room"
        const val EXTRA_TIME = "course_time"
    }
}
