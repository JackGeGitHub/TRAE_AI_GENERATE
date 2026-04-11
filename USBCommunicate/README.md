# USBCommunicate

Android USB通信应用，支持Android 14平台的HOST和SLAVE模式USB通信。

## 功能特点

1. **双模式支持**：可选择HOST模式或SLAVE模式
2. **Control Transfer通信**：
   - 通过controlTransfer设置波特率（默认115200）和数据位
   - 1秒后通过controlTransfer读取返回值
3. **Bulk Transfer通信**：
   - 通过bulkTransfer发送命令："nvs set nvs app_mode 1\r"
   - 1秒后通过bulkTransfer读取返回值
4. **实时日志**：显示详细的通信日志

## 使用说明

### HOST设备设置
1. 将应用设置为HOST模式
2. 点击"Connect Device"按钮连接USB设备
3. 点击"Send Control Command"发送controlTransfer命令
4. 点击"Send Bulk Command"发送bulkTransfer命令

### SLAVE设备设置
1. 将应用设置为SLAVE模式
2. 等待HOST设备连接

## 技术要求

- Android 5.0 (API 21) 及以上
- 支持USB Host模式的设备
- USB OTG线（用于HOST模式）

## 项目结构

```
USBCommunicate/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/usb/communicate/
│   │       │   └── MainActivity.java
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   ├── colors.xml
│   │       │   │   └── themes.xml
│   │       │   └── xml/
│   │       │       └── device_filter.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

