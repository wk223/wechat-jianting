package com.xiaoliu.wechatprint

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.OutputStream
import java.util.UUID

class BlePrinter(private val context: Context) {

    companion object {
        const val TAG = "BlePrinter"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val queue = java.util.concurrent.LinkedBlockingQueue<PrintJob>()
    private var running = false

    data class PrintJob(val group: String, val sender: String, val content: String, val time: String)

    fun connect(mac: String) {
        if (running) return
        running = true
        Thread {
            while (running) {
                try {
                    val job = queue.take()
                    doPrint(job)
                } catch (e: InterruptedException) { break }
            }
        }.start()
    }

    fun print(group: String, sender: String, content: String, time: String) {
        queue.offer(PrintJob(group, sender, content, time))
    }

    private fun doPrint(job: PrintJob) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter.cancelDiscovery()
            val device = adapter.bondedDevices.firstOrNull() ?: run {
                Log.e(TAG, "没有配对的打印机"); return
            }
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val os: OutputStream = socket.outputStream

            // ESC/POS 指令手写，不依赖任何库
            val ESC_INIT   = byteArrayOf(0x1B, 0x40)
            val ALIGN_CTR  = byteArrayOf(0x1B, 0x61, 0x01)
            val ALIGN_LFT  = byteArrayOf(0x1B, 0x61, 0x00)
            val BOLD_ON    = byteArrayOf(0x1B, 0x45, 0x01)
            val BOLD_OFF   = byteArrayOf(0x1B, 0x45, 0x00)
            val LF         = byteArrayOf(0x0A)
            val CUT        = byteArrayOf(0x1D, 0x56, 0x01)

            fun w(b: ByteArray) = os.write(b)
            fun t(s: String) = os.write(s.toByteArray(charset("GBK")))
            fun nl()            = w(LF)

            w(ESC_INIT)
            w(byteArrayOf(0x1D, 0x21, 0x11)) // 字体放大2倍
            w(ALIGN_LFT)
            w(BOLD_ON); t("来自:"); w(BOLD_OFF); t(job.sender.take(12)); nl()
            w(BOLD_ON); t("群: "); w(BOLD_OFF); t(job.group.take(12)); nl()
            t(job.time); nl()
            // 60mm高度足够，内容显示更多，每行约8个中文字，分多行显示
            val cleanContent = job.content.trimStart { it.isDigit() || it == '[' || it == ']' || it == ' ' }
            val lines = cleanContent.chunked(8)
            lines.take(6).forEach { t(it); nl() }
            // 强制走满60mm，60mm约等于12行（203dpi），内容占了几行就补剩余的空行
            val contentLines = 3 + lines.take(6).size  // 来自+群+时间+内容行数
            val emptyLines = maxOf(12 - contentLines, 6)
            repeat(emptyLines) { nl() }
            w(CUT)
            os.flush()
            Log.i(TAG, "打印成功: ${job.sender}")

        } catch (e: Exception) {
            Log.e(TAG, "打印失败: ${e.message}")
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    fun disconnect() {
        running = false
        queue.clear()
    }
}
