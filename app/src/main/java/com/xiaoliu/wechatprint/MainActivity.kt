package com.xiaoliu.wechatprint

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnPermission: Button
    private lateinit var btnTestPrint: Button
    private lateinit var btnTestNotify: Button
    private lateinit var spinnerPrinters: Spinner
    private var scannedDevices: List<BluetoothDevice> = emptyList()
    private var selectedDevice: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus        = findViewById(R.id.tvStatus)
        btnScan         = findViewById(R.id.btnScan)
        btnPermission   = findViewById(R.id.btnPermission)
        btnTestPrint    = findViewById(R.id.btnTestPrint)
        btnTestNotify   = findViewById(R.id.btnTestNotify)
        spinnerPrinters = findViewById(R.id.spinnerPrinters)

        btnPermission.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("需要通知访问权限")
                .setMessage("请在下一页找到「@消息监控」并开启「通知使用权」")
                .setPositiveButton("去授权") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .show()
        }

        btnScan.setOnClickListener { scanPairedDevices() }

        // ── 测试按钮1：直接测试打印机连接 ──────────────────────────
        btnTestPrint.setOnClickListener {
            val mac = getSavedPrinterAddress()
            if (mac.isEmpty()) {
                Toast.makeText(this, "请先扫描并选择打印机", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "正在连接打印机测试..."
            val printer = BlePrinter(this)
            printer.connect(mac)
            // 延迟3秒等待连接，然后发送测试打印
            android.os.Handler(mainLooper).postDelayed({
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                printer.print(
                    group   = "测试群组",
                    sender  = "测试发送者",
                    content = "@小刘刘 这是一条测试打印消息，如果你看到这张纸，说明打印机连接正常！",
                    time    = time
                )
                tvStatus.text = "测试打印指令已发送\n如果没出纸请检查打印机是否开启"
            }, 3000)
        }

        // ── 测试按钮2：模拟一条微信@通知触发完整流程 ───────────────
        btnTestNotify.setOnClickListener {
            val mac = getSavedPrinterAddress()
            if (mac.isEmpty()) {
                Toast.makeText(this, "请先扫描并选择打印机", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "模拟@通知，触发完整打印流程..."
            // 直接调用 NotificationService 的打印逻辑（绕过通知监听）
            val printer = BlePrinter(this)
            printer.connect(mac)
            android.os.Handler(mainLooper).postDelayed({
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                printer.print(
                    group   = "产品讨论群",
                    sender  = "张三",
                    content = "@小刘刘 下午三点开会，模拟通知测试成功！",
                    time    = time
                )
                tvStatus.text = "模拟@通知已发送打印机\n结果：\n· 有出纸 → 打印机正常，问题在通知监听\n· 没出纸 → 打印机连接有问题"
            }, 3000)
        }

        spinnerPrinters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                if (scannedDevices.isNotEmpty()) {
                    selectedDevice = scannedDevices[pos]
                    savePrinterAddress(selectedDevice!!.address)
                    tvStatus.text = "已选择打印机：${selectedDevice!!.name}\n\n点击「测试打印」验证连接是否正常"
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
            "通知权限：已授权\n\n请先点「测试打印」确认打印机连接正常\n再点「模拟@通知」测试完整流程"
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
        scannedDevices = adapter.bondedDevices.toList()
        if (scannedDevices.isEmpty()) {
            Toast.makeText(this, "没有已配对的蓝牙设备，请先在手机蓝牙设置里配对打印机", Toast.LENGTH_LONG).show()
            return
        }
        val names = scannedDevices.map { "${it.name}  (${it.address})" }
        spinnerPrinters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
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
