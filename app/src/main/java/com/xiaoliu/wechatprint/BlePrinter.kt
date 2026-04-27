package com.xiaoliu.wechatprint

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.UUID

class BlePrinter(private val context: Context) {

    companion object {
        const val TAG = "BlePrinter"
        // 大多数蓝牙热敏打印机通用的 SPP over BLE 服务UUID
        val PRINT_SERVICE_UUID: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
        val PRINT_CHAR_UUID:    UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")
    }

    private var gatt: BluetoothGatt? = null
    private var printChar: BluetoothGattCharacteristic? = null
    private var targetAddress: String = ""

    // ESC/POS 基础指令
    private val ESC_INIT      = byteArrayOf(0x1B, 0x40)           // 初始化打印机
    private val ESC_ALIGN_CTR = byteArrayOf(0x1B, 0x61, 0x01)    // 居中
    private val ESC_ALIGN_LFT = byteArrayOf(0x1B, 0x61, 0x00)    // 左对齐
    private val ESC_BOLD_ON   = byteArrayOf(0x1B, 0x45, 0x01)    // 粗体开
    private val ESC_BOLD_OFF  = byteArrayOf(0x1B, 0x45, 0x00)    // 粗体关
    private val ESC_FEED      = byteArrayOf(0x0A)                  // 换行
    private val ESC_CUT       = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // 切纸

    // ── 连接到指定MAC地址的打印机 ─────────────────────────────────
    fun connect(macAddress: String) {
        targetAddress = macAddress
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Log.e(TAG, "设备不支持蓝牙")
            return
        }
        val device = adapter.getRemoteDevice(macAddress)
        gatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.i(TAG, "正在连接打印机: $macAddress")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "打印机已连接，正在发现服务...")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "打印机断开，5秒后重连...")
                printChar = null
                Thread.sleep(5000)
                g.connect()   // 自动重连
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            printChar = g.getService(PRINT_SERVICE_UUID)
                ?.getCharacteristic(PRINT_CHAR_UUID)
            if (printChar != null) {
                Log.i(TAG, "打印特征值已就绪")
            } else {
                Log.e(TAG, "未找到打印特征值，请确认打印机型号")
            }
        }
    }

    // ── 打印一条@消息 ─────────────────────────────────────────────
    fun print(group: String, sender: String, content: String, time: String) {
        val char = printChar ?: run {
            Log.e(TAG, "打印机未连接，消息丢弃: $content")
            return
        }

        val bytes = buildPrintBytes(group, sender, content, time)

        // BLE MTU限制（通常20字节），需要分包发送
        bytes.toList().chunked(20).forEach { chunk ->
            char.value = chunk.toByteArray()
            gatt?.writeCharacteristic(char)
            Thread.sleep(30)   // 每包之间稍等，避免丢包
        }

        Log.i(TAG, "打印完成: [$group] $sender")
    }

    // ── 组装打印字节流（ESC/POS格式）─────────────────────────────
    private fun buildPrintBytes(
        group: String, sender: String, content: String, time: String
    ): ByteArray {
        val buf = mutableListOf<Byte>()

        fun add(b: ByteArray) = buf.addAll(b.toList())
        fun addText(s: String) = buf.addAll(s.toByteArray(Charsets.UTF_8).toList())
        fun newLine() = add(ESC_FEED)

        add(ESC_INIT)

        // ── 标题行：居中 + 粗体 ──
        add(ESC_ALIGN_CTR)
        add(ESC_BOLD_ON)
        addText("[ 微信群 @消息 ]")
        newLine()
        add(ESC_BOLD_OFF)

        // ── 分割线 ──
        add(ESC_ALIGN_LFT)
        addText("--------------------------------")
        newLine()

        // ── 群名 ──
        add(ESC_BOLD_ON)
        addText("群：")
        add(ESC_BOLD_OFF)
        addText(group)
        newLine()

        // ── 发送者 ──
        add(ESC_BOLD_ON)
        addText("来自：")
        add(ESC_BOLD_OFF)
        addText(sender)
        newLine()

        // ── 时间 ──
        add(ESC_BOLD_ON)
        addText("时间：")
        add(ESC_BOLD_OFF)
        addText(time)
        newLine()

        // ── 分割线 ──
        addText("--------------------------------")
        newLine()

        // ── 消息内容（主体）──
        addText(content)
        newLine()
        newLine()

        // ── 走纸 + 切纸 ──
        newLine()
        newLine()
        add(ESC_CUT)

        return buf.toByteArray()
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
