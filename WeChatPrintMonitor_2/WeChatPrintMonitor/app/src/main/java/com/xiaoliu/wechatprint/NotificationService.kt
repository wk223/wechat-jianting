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
        const val MY_NICKNAME = "朱磊"          // 匹配开头，兼容后面多余字符
        const val CHANNEL_ID = "wechat_print_fg"
        const val NOTIF_ID = 1001
    }

    private lateinit var printer: BlePrinter
    private val dedupeCache = LinkedHashMap<String, Long>(50, 0.75f, true)
    private val DEDUPE_WINDOW_MS = 10_000L   // 10秒内相同消息不重复打印

    // ── 服务启动 ─────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        printer = BlePrinter(this)
        startForegroundService()
        Log.i(TAG, "通知监听服务已启动")
    }

    // ── 前台服务：让系统不杀掉我们 ───────────────────────────────
    private fun startForegroundService() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "微信@消息监控",
                NotificationManager.IMPORTANCE_LOW   // LOW = 无声音，不打扰用户
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
            .setContentText("正在监听微信群 @朱磊 消息")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openIntent)
            .setOngoing(true)          // 不可被用户手动清除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    // ── 收到新通知时的回调（核心入口）────────────────────────────
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 只处理微信通知
        if (sbn.packageName != WECHAT_PACKAGE) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return   // 群名 or 发送者
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        Log.d(TAG, "微信通知 | 标题: $title | 内容: $text")

        // 判断是否@了我（匹配"朱磊"开头，兼容后续字符）
        if (!isMentionMe(text)) return

        // 解析发送者和消息内容
        // 微信群通知格式："发送者: 消息内容"
        val (sender, content) = parseText(text)
        val group = title
        val time  = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        // 去重：10秒内同一条消息不重复打印
        val dedupeKey = "$group|$sender|$content"
        val now = System.currentTimeMillis()
        if (dedupeCache[dedupeKey]?.let { now - it < DEDUPE_WINDOW_MS } == true) {
            Log.d(TAG, "重复消息，跳过打印")
            return
        }
        dedupeCache[dedupeKey] = now
        // 缓存超过100条时清理最旧的
        if (dedupeCache.size > 100) {
            dedupeCache.iterator().apply { next(); remove() }
        }

        Log.i(TAG, "触发打印 | 群: $group | 发送者: $sender")
        printer.print(group, sender, content, time)
    }

    // ── @判断：包含"@朱磊"（后面可跟任意字符）──────────────────
    private fun isMentionMe(text: String): Boolean {
        // 正则：@朱磊 后面跟0个或多个非空白字符（兼容你说的那个打不出的字符）
        val pattern = Regex("@${Regex.escape(MY_NICKNAME)}\\S*")
        return pattern.containsMatchIn(text)
    }

    // ── 解析"发送者: 消息内容"格式 ────────────────────────────────
    private fun parseText(text: String): Pair<String, String> {
        val colonIdx = text.indexOf(": ")
        return if (colonIdx > 0) {
            text.substring(0, colonIdx).trim() to text.substring(colonIdx + 2).trim()
        } else {
            "未知" to text
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printer.disconnect()
        Log.i(TAG, "服务已停止")
    }
}
