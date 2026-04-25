package com.xiaoliu.wechatprint

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnPermission: Button
    private lateinit var spinnerPrinters: Spinner
    private var scannedDevices: List<BluetoothDevice> = emptyList()
    private var selectedDevice: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus       = findViewById(R.id.tvStatus)
        btnScan        = findViewById(R.id.btnScan)
        btnPermission  = findViewById(R.id.btnPermission)
        spinnerPrinters = findViewById(R.id.spinnerPrinters)

        // ── 按钮：前往授权通知权限 ──
        btnPermission.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("需要通知访问权限")
                .setMessage("请在下一页找到「@消息监控」并开启「通知使用权」，这样App才能接收所有微信群的@消息。")
                .setPositiveButton("去授权") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .show()
        }

        // ── 按钮：扫描已配对蓝牙设备 ──
        btnScan.setOnClickListener {
            scanPairedDevices()
        }

        // ── Spinner：选择打印机后保存MAC地址 ──
        spinnerPrinters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                if (scannedDevices.isNotEmpty()) {
                    selectedDevice = scannedDevices[pos]
                    savePrinterAddress(selectedDevice!!.address)
                    tvStatus.text = "已选择打印机：${selectedDevice!!.name}"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val granted = isNotificationListenerEnabled()
        tvStatus.text = if (granted) {
            "通知权限：已授权\n监听服务运行中"
        } else {
            "通知权限：未授权\n请点击下方按钮去授权"
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun scanPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        // 列出所有已配对设备（热敏打印机需要先在手机蓝牙设置里配对）
        scannedDevices = adapter.bondedDevices.toList()
        if (scannedDevices.isEmpty()) {
            Toast.makeText(this, "没有已配对的蓝牙设备，请先在手机蓝牙设置里配对打印机", Toast.LENGTH_LONG).show()
            return
        }

        val names = scannedDevices.map { "${it.name}  (${it.address})" }
        spinnerPrinters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 恢复上次选中的打印机
        val savedAddr = getSavedPrinterAddress()
        val idx = scannedDevices.indexOfFirst { it.address == savedAddr }
        if (idx >= 0) spinnerPrinters.setSelection(idx)
    }

    private fun savePrinterAddress(address: String) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
            .putString("printer_mac", address).apply()
    }

    private fun getSavedPrinterAddress(): String {
        return getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("printer_mac", "") ?: ""
    }
}
