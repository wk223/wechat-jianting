package com.xiaoliu.wechatprint

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var printer: BlePrinter? = null

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

        // 测试按钮1：直接测试打印机出纸
        btnTestPrint.setOnClickListener {
            val mac = getSavedPrinterAddress()
            if (mac.isEmpty()) {
                toast("请先扫描并选择打印机")
                return@setOnClickListener
            }
            tvStatus.text = "正在连接打印机，请等待3秒..."
            val p = BlePrinter(this)
            printer = p
            p.connect(mac)
            Handler(Looper.getMainLooper()).postDelayed({
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                p.print("测试群", "测试用户", "@小刘刘 打印机连接测试，收到请查收！", time)
                tvStatus.text = "打印指令已发送\n· 有出纸 → 打印机正常✅\n· 没出纸 → 检查打印机是否开机并已配对"
            }, 3000)
        }

        // 测试按钮2：模拟完整@通知流程
        btnTestNotify.setOnClickListener {
            val mac = getSavedPrinterAddress()
            if (mac.isEmpty()) {
                toast("请先扫描并选择打印机")
                return@setOnClickListener
            }
            tvStatus.text = "模拟@通知中，请等待3秒..."
            val p = BlePrinter(this)
            printer = p
            p.connect(mac)
            Handler(Looper.getMainLooper()).postDelayed({
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                p.print("产品讨论群", "张三", "@小刘刘 模拟通知测试，完整流程验证！", time)
                tvStatus.text = "模拟通知已发送\n· 有出纸 → 完整流程正常✅，问题在系统通知限制\n· 没出纸 → 蓝牙连接有问题"
            }, 3000)
        }

        spinnerPrinters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: android.view.View?, pos: Int, id: Long) {
                if (scannedDevices.isNotEmpty()) {
                    val device = scannedDevices[pos]
                    savePrinterAddress(device.address)
                    tvStatus.text = "已选择：${device.name}\n点击「③测试打印」验证连接"
                }
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val granted = isNotificationListenerEnabled()
        tvStatus.text = if (granted)
            "通知权限：已授权✅\n\n先点②扫描打印机\n再点③测试打印机连接\n最后点④测试完整流程"
        else
            "通知权限：未授权❌\n请点击①去授权"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun scanPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            toast("请先开启蓝牙")
            return
        }
        scannedDevices = adapter.bondedDevices.toList()
        if (scannedDevices.isEmpty()) {
            toast("没有已配对设备，请先在手机蓝牙设置里配对打印机")
            return
        }
        val names = scannedDevices.map { "${it.name}  (${it.address})" }
        spinnerPrinters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val savedAddr = getSavedPrinterAddress()
        val idx = scannedDevices.indexOfFirst { it.address == savedAddr }
        if (idx >= 0) spinnerPrinters.setSelection(idx)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun savePrinterAddress(address: String) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
            .putString("printer_mac", address).apply()
    }

    private fun getSavedPrinterAddress(): String {
        return getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("printer_mac", "") ?: ""
    }
}
