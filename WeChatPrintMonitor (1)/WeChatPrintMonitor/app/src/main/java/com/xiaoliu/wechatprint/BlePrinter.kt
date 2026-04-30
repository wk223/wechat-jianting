package com.xiaoliu.wechatprint

import android.content.Context
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

class BlePrinter(private val context: Context) {

    companion object {
        const val TAG = "BlePrinter"
    }

    private val printQueue = java.util.concurrent.LinkedBlockingQueue<PrintJob>()
    private var workerRunning = false

    data class PrintJob(
        val group: String,
        val sender: String,
        val content: String,
        val time: String
    )

    fun connect(macAddress: String) {
        startWorker()
    }

    private fun startWorker() {
        if (workerRunning) return
        workerRunning = true
        Thread {
            Log.i(TAG, "打印队列启动")
            while (workerRunning) {
                try {
                    val job = printQueue.take()
                    executePrint(job)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.start()
    }

    fun print(group: String, sender: String, content: String, time: String) {
        printQueue.offer(PrintJob(group, sender, content, time))
        Log.i(TAG, "加入队列: $sender -> $group")
    }

    private fun executePrint(job: PrintJob) {
        var retryCount = 0
        while (retryCount < 3) {
            try {
                val connection = BluetoothPrintersConnections.selectFirstPaired()
                    ?: run {
                        Log.e(TAG, "没有找到已配对的蓝牙打印机")
                        return
                    }

                // 40mm纸宽，203dpi，每行16字符
                val printer = EscPosPrinter(connection, 203, 40f, 16)

                // 格式适配40mm x 30mm
                // sender = @你的那个人的昵称（核心内容）
                printer.printFormattedTextAndCut(
                    "[C]<b>[ @ 我 ]</b>\n" +
                    "[L]<b>来自:</b>${job.sender.take(8)}\n" +
                    "[L]<b>群:</b>${job.group.take(8)}\n" +
                    "[L]<b>时间:</b>${job.time}\n" +
                    "[L]${job.content.take(18)}\n"
                )

                Log.i(TAG, "打印成功: ${job.sender}")
                return

            } catch (e: Exception) {
                retryCount++
                Log.e(TAG, "打印失败(第${retryCount}次): ${e.message}")
                if (retryCount < 3) Thread.sleep(1500)
            }
        }
        Log.e(TAG, "重试3次失败")
    }

    fun disconnect() {
        workerRunning = false
        printQueue.clear()
    }
}
