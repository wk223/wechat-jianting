package com.xiaoliu.wechatprint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启动接收器
 * 手机重启后自动拉起通知监听服务，无需手动打开App
 * 注意：NotificationListenerService 由系统绑定，
 *       开机后只需确保 App 进程存在即可，系统会自动重新绑定已授权的监听服务
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "开机完成，拉起主界面以恢复服务")
            // 启动 MainActivity 让进程存活，系统随后会重新绑定 NotificationListenerService
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launch)
        }
    }
}
