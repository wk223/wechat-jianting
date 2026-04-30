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
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnPermission: Button
    private lateinit var btnTestPrint: Button
    private lateinit var btnTestNotify: Button
    private lateinit var btnUpdate: Button
    private lateinit var spinnerPrinters: Spinner
    private var scannedDevices: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus        = findViewById(R.id.tvStatus)
        btnScan         = findViewById(R.id.btnScan)
        btnPermission   = findViewById(R.id.btnPermission)
        btnTestPrint    = findViewById(R.id.btnTestPrint)
        btnTestNotify   = findViewById(R.id.btnTestNotify)
        btnUpdate       = findViewById(R.id.btnUpdate)
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

        btnTestPrint.setOnClickListener {
            val mac = getSavedPrinterAddress()
            if (mac.isEmpty()) { toast("请先扫描并选择打印机"); return@setOnClickListener }
            tvStatus.text = "正在连接，2秒后打印测试..."
            val p = BlePrinter(this)
            p.connect(mac)
            Handler(Looper.getMainLooper()).postDelayed({
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                p.print("测试群", "张三", "@小刘刘 打印机测试成功！", time)
                tvStatus.text = "测试指令已发送\n· 有出纸 ✅ 打印机正常\n· 没出纸 ❌ 检查打印机配对"
            }, 2000)
        }

        btnTestNotify.setOnClickListener {
            val mac = getSavedPrinterAddress()
            if (mac.isEmpty()) { toast("请先扫描并选择打印机"); return@setOnClickListener }
            tvStatus.text = "模拟@通知中..."
            val p = BlePrinter(this)
            p.connect(mac)
            Handler(Looper.getMainLooper()).postDelayed({
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                p.print("产品讨论群", "李四", "@小刘刘 下午三点开会记得带资料", time)
                tvStatus.text = "模拟通知已发送\n· 有出纸 ✅ 完整流程正常\n· 没出纸 ❌ 蓝牙连接问题"
            }, 2000)
        }

        // 检查更新按钮
        btnUpdate.setOnClickListener {
            tvStatus.text = "正在检查更新..."
            val updater = UpdateManager(this)
            updater.checkUpdate(object : UpdateManager.Callback {
                override fun onNewVersion(ver: String) {
                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("发现新版本 v$ver")
                            .setMessage("是否立即下载安装？")
                            .setPositiveButton("立即更新") { _, _ ->
                                tvStatus.text = "正在下载..."
                                updater.download(object : UpdateManager.Callback {
                                    override fun onNewVersion(ver: String) {}
                                    override fun onLatest() {}
                                    override fun onDownloading() {
                                        runOnUiThread { tvStatus.text = "下载中，请稍候..." }
                                    }
                                    override fun onDone(file: File) {
                                        runOnUiThread {
                                            tvStatus.text = "下载完成，正在安装..."
                                            updater.install(file)
                                        }
                                    }
                                    override fun onError(msg: String) {
                                        runOnUiThread { tvStatus.text = "下载失败: $msg" }
                                    }
                                })
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
                override fun onLatest() {
                    runOnUiThread { tvStatus.text = "已是最新版本 v${UpdateManager.CUR_VERSION}" }
                }
                override fun onDownloading() {}
                override fun onDone(file: File) {}
                override fun onError(msg: String) {
                    runOnUiThread { tvStatus.text = "检查更新失败: $msg" }
                }
            })
        }

        spinnerPrinters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: android.view.View?, pos: Int, id: Long) {
                if (scannedDevices.isNotEmpty()) {
                    val device = scannedDevices[pos]
                    savePrinterAddress(device.address)
                    tvStatus.text = "已选择：${device.name}\n点击③测试连接"
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
            "通知权限：已授权 ✅\n当前版本：v${UpdateManager.CUR_VERSION}\n\n先点②扫描打印机\n再点③测试连接"
        else
            "通知权限：未授权 ❌\n请点①去授权"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun scanPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { toast("请先开启蓝牙"); return }
        scannedDevices = adapter.bondedDevices.toList()
        if (scannedDevices.isEmpty()) { toast("没有已配对设备，请先配对打印机"); return }
        val names = scannedDevices.map { "${it.name}  (${it.address})" }
        spinnerPrinters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val idx = scannedDevices.indexOfFirst { it.address == getSavedPrinterAddress() }
        if (idx >= 0) spinnerPrinters.setSelection(idx)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun savePrinterAddress(a: String) = getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("printer_mac", a).apply()
    private fun getSavedPrinterAddress() = getSharedPreferences("prefs", MODE_PRIVATE).getString("printer_mac", "") ?: ""
}
