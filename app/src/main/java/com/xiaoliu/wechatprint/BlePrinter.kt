package com.xiaoliu.wechatprint

import android.content.Context
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

class BlePrinter(private val context: Context) {

    companion object {
        const val TAG = "BlePrinter"
    }

    private var targetMac: String = ""

    fun connect(macAddress: String) {
        targetMac = macAddress
    }

    fun print(group: String, sender: String, content: String, time: String) {
        Thread {
            try {
                // 自动找到已配对的打印机并连接
                val connection = BluetoothPrintersConnections.selectFirstPaired()
                    ?: run {
                        Log.e(TAG, "没有找到已配对的蓝牙打印机")
                        return@Thread
                    }

                val printer = EscPosPrinter(connection, 203, 58f, 32)
                printer.printFormattedTextAndCut(
                    "[C]<b>[ 微信群 @消息 ]</b>\n" +
                    "[L]--------------------------------\n" +
                    "[L]<b>群：</b>$group\n" +
                    "[L]<b>来自：</b>$sender\n" +
                    "[L]<b>时间：</b>$time\n" +
                    "[L]--------------------------------\n" +
                    "[L]$content\n" +
                    "[L]\n" +
                    "[L]\n"
                )
                Log.i(TAG, "打印成功")
            } catch (e: Exception) {
                Log.e(TAG, "打印失败: ${e.message}")
            }
        }.start()
    }

    fun disconnect() {
        // 库自动管理连接，无需手动断开
    }
}
