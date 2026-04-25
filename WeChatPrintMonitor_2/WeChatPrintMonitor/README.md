# 微信 @消息打印监控 — 使用说明

## 项目结构

```
WeChatPrintMonitor/
├── app/src/main/
│   ├── AndroidManifest.xml          # 权限 + 服务注册
│   ├── java/com/xiaoliu/wechatprint/
│   │   ├── NotificationService.kt   # 核心：通知监听 + 过滤 + 打印触发
│   │   ├── BlePrinter.kt            # 蓝牙BLE连接 + ESC/POS打印
│   │   ├── MainActivity.kt          # 设置界面
│   │   └── BootReceiver.kt          # 开机自启动
│   └── res/layout/
│       └── activity_main.xml        # 主界面布局
└── app/build.gradle                 # 依赖配置
```

## 编译方法（需要 Android Studio）

1. 用 Android Studio 打开整个 WeChatPrintMonitor 文件夹
2. 等待 Gradle 同步完成
3. 点击菜单 Build → Build APK
4. APK 在 app/build/outputs/apk/debug/ 目录下

## 首次安装后的设置步骤

1. **授权通知权限**
   - 打开 App，点击「授权通知访问权限」
   - 在系统设置页找到「@消息监控」，打开开关

2. **配对打印机**
   - 去手机「设置 → 蓝牙」，搜索并配对你的热敏打印机

3. **选择打印机**
   - 回到 App，点击「扫描已配对蓝牙打印机」
   - 在下拉列表里选择你的打印机

4. **后台常驻设置（小米/华为必做）**
   - 小米：设置 → 应用 → @消息监控 → 省电策略 → 无限制
   - 华为：设置 → 应用 → @消息监控 → 电池 → 允许后台活动

5. **完成！**
   - 最小化 App，微信群里有人 @小刘刘，打印机立即出纸

## 打印输出格式

```
[ 微信群 @消息 ]
--------------------------------
群：产品讨论群
来自：张三
时间：14:32:07
--------------------------------
@小刘刘 下午开会记得带资料
```

## 注意事项

- 打印机需支持 ESC/POS 协议（市面上绝大多数热敏打印机均支持）
- 如打印乱码，检查打印机是否支持 UTF-8 中文编码
- 如连接失败，尝试在 BlePrinter.kt 中替换 UUID 为打印机厂商提供的专用 UUID
- App 会在手机重启后自动恢复监听，无需手动打开
