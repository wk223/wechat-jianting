package com.xiaoliu.wechatprint

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    companion object {
        const val TAG = "UpdateManager"
        const val APK_URL     = "https://wwaou.lanzoup.com/ikTmU3ocx2kf"
        const val VER_URL     = "https://wwaou.lanzoup.com/iPhT83ocwxoj"
        const val VER_PWD     = "hg3u"
        const val CUR_VERSION = "1.0"
    }

    interface Callback {
        fun onNewVersion(ver: String)
        fun onLatest()
        fun onDownloading()
        fun onDone(file: File)
        fun onError(msg: String)
    }

    fun checkUpdate(cb: Callback) {
        Thread {
            try {
                val ver = fetchText(VER_URL, VER_PWD) ?: run { cb.onError("获取版本失败"); return@Thread }
                if (ver.trim() != CUR_VERSION) cb.onNewVersion(ver.trim()) else cb.onLatest()
            } catch (e: Exception) { cb.onError(e.message ?: "未知错误") }
        }.start()
    }

    fun download(cb: Callback) {
        Thread {
            try {
                cb.onDownloading()
                val url = getDirectUrl(APK_URL) ?: run { cb.onError("解析地址失败"); return@Thread }
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
                if (file.exists()) file.delete()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                cb.onDone(file)
            } catch (e: Exception) { cb.onError(e.message ?: "下载失败") }
        }.start()
    }

    fun install(file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        else Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // 解析蓝奏云真实下载地址
    private fun getDirectUrl(shareUrl: String): String? {
        return try {
            val conn = URL(shareUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // 提取 iframe src
            val iframe = Regex("""iframe[^>]+src=['"]([^'"]+)['"]""").find(html)
                ?.groupValues?.get(1) ?: return null
            val iframeUrl = if (iframe.startsWith("http")) iframe else "https://wwaou.lanzoup.com$iframe"

            val conn2 = URL(iframeUrl).openConnection() as HttpURLConnection
            conn2.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn2.setRequestProperty("Referer", shareUrl)
            val html2 = conn2.inputStream.bufferedReader().readText()
            conn2.disconnect()

            // 提取真实URL
            Regex(""""url"\s*:\s*"([^"]+)"""").find(html2)
                ?.groupValues?.get(1)?.replace("\\/", "/")
        } catch (e: Exception) {
            Log.e(TAG, "解析失败: ${e.message}")
            null
        }
    }

    // 获取版本号文本（带密码的蓝奏云）
    private fun fetchText(shareUrl: String, pwd: String): String? {
        return try {
            val conn = URL(shareUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // 提取文件ID和sign用于带密码请求
            val fileId = Regex("""'file_id'\s*,\s*'(\d+)'""").find(html)?.groupValues?.get(1)
            val sign = Regex("""'sign'\s*,\s*'([^']+)'""").find(html)?.groupValues?.get(1)

            if (fileId != null && sign != null) {
                val postUrl = "https://wwaou.lanzoup.com/ajaxm.php"
                val postData = "action=downprocess&signs=$sign&sign=$sign&ves=1&pwd=$pwd"
                val conn2 = URL(postUrl).openConnection() as HttpURLConnection
                conn2.requestMethod = "POST"
                conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn2.setRequestProperty("Referer", shareUrl)
                conn2.doOutput = true
                conn2.outputStream.write(postData.toByteArray())
                val resp = conn2.inputStream.bufferedReader().readText()
                conn2.disconnect()

                // 从JSON中提取url
                val url = Regex(""""url"\s*:\s*"([^"]+)"""").find(resp)
                    ?.groupValues?.get(1)?.replace("\\/", "/") ?: return null

                val conn3 = URL(url).openConnection() as HttpURLConnection
                conn3.setRequestProperty("User-Agent", "Mozilla/5.0")
                val text = conn3.inputStream.bufferedReader().readText()
                conn3.disconnect()
                text
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "获取版本失败: ${e.message}")
            null
        }
    }
}
