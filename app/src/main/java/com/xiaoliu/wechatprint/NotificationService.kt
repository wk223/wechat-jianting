package com.xiaoliu.wechatprint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NotificationService : NotificationListenerService() {

    companion object {
        const val TAG = "WeChatPrint"
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val MY_NICKNAME = "小刘刘"
        const val CHANNEL_ID = "wechat_print_fg"
        const val NOTIF_ID = 1001
    }

    private lateinit var printer: BlePrinter
    private val dedupeCache = LinkedHashMap<String, Long>(50, 0.75f, true)
    private val DEDUPE_WINDOW_MS = 10_000L

    override fun onCreate() {
        super.onCreate()
        printer = BlePrinter(this)
        startForegroundService()
        val mac = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("printer_mac", "") ?: ""
        printer.connect(mac)
        Log.i(TAG, "服务启动，打印队列就绪")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知监听已绑定")
    }

    private fun startForegroundService() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "微信@消息监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台监听微信@消息并打印" }
            manager.createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("@消息监控运行中")
            .setContentText("监听 @小刘刘 消息中")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WECHAT_PACKAGE) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
    ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return

        Log.d(TAG, "微信通知 | $title | $text")

        if (!isMentionMe(text)) return

        val (sender, content) = parseText(text)
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        val dedupeKey = "$title|$sender|$content"
        val now = System.currentTimeMillis()
        if (dedupeCache[dedupeKey]?.let { now - it < DEDUPE_WINDOW_MS } == true) return
        dedupeCache[dedupeKey] = now
        if (dedupeCache.size > 100) dedupeCache.iterator().apply { next(); remove() }

        Log.i(TAG, "触发打印: $sender @ $title")
        printer.print(title, sender, content, time)
    }

    private fun isMentionMe(text: String): Boolean {
        return Regex("@${Regex.escape(MY_NICKNAME)}\\S*").containsMatchIn(text)
    }

    private fun parseText(text: String): Pair<String, String> {
        val colonIdx = text.indexOf(": ")
        return if (colonIdx > 0)
            text.substring(0, colonIdx).trim() to text.substring(colonIdx + 2).trim()
        else "未知" to text
    }

    override fun onDestroy() {
        super.onDestroy()
        printer.disconnect()
    }
}
