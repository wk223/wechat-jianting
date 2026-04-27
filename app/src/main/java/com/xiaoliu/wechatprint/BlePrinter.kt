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
        // SPP 标准 UUID，所有热敏打印机通用
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var macAddress: String = ""

    // ESC/POS 指令
    private val ESC_INIT      = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_CTR = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_LFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_BOLD_ON   = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF  = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_FEED      = byteArrayOf(0x0A)
    private val ESC_CUT       = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    fun connect(mac: String) {
        macAddress = mac
        connectInternal()
    }

    private fun connectInternal() {
        try {
            disconnect() // 先断开旧连接
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            val device = adapter.getRemoteDevice(macAddress)
            // 用反射创建 SPP Socket，兼容性更好
            socket = device.javaClass
                .getMethod("createRfcommSocket", Int::class.java)
                .invoke(device, 1) as BluetoothSocket
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            socket!!.connect()
            outputStream = socket!!.outputStream
            Log.i(TAG, "打印机已连接: $macAddress")
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}")
            socket = null
            outputStream = null
        }
    }

    fun print(group: String, sender: String, content: String, time: String) {
        // 如果未连接，先尝试连接
        if (outputStream == null) {
            Log.w(TAG, "未连接，尝试连接...")
            connectInternal()
        }

        val out = outputStream ?: run {
            Log.e(TAG, "打印机未连接，消息丢弃: $content")
            return
        }

        try {
            val bytes = buildPrintBytes(group, sender, content, time)
            out.write(bytes)
            out.flush()
            Log.i(TAG, "打印完成: [$group] $sender")
        } catch (e: Exception) {
            Log.e(TAG, "打印失败: ${e.message}")
            // 连接断了，下次打印时重连
            socket = null
            outputStream = null
        }
    }

    private fun buildPrintBytes(
        group: String, sender: String, content: String, time: String
    ): ByteArray {
        val buf = mutableListOf<Byte>()

        fun add(b: ByteArray) = buf.addAll(b.toList())
        fun addText(s: String) = buf.addAll(s.toByteArray(Charsets.GBK).toList()) // 中文用GBK
        fun newLine() = add(ESC_FEED)

        add(ESC_INIT)

        add(ESC_ALIGN_CTR)
        add(ESC_BOLD_ON)
        addText("[ 微信群 @消息 ]")
        newLine()
        add(ESC_BOLD_OFF)

        add(ESC_ALIGN_LFT)
        addText("--------------------------------")
        newLine()

        add(ESC_BOLD_ON); addText("群："); add(ESC_BOLD_OFF)
        addText(group); newLine()

        add(ESC_BOLD_ON); addText("来自："); add(ESC_BOLD_OFF)
        addText(sender); newLine()

        add(ESC_BOLD_ON); addText("时间："); add(ESC_BOLD_OFF)
        addText(time); newLine()

        addText("--------------------------------"); newLine()

        addText(content); newLine(); newLine()

        newLine(); newLine()
        add(ESC_CUT)

        return buf.toByteArray()
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
    }
}
