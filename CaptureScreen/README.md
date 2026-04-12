# Android 15 区域截屏工具

基于 Android 15（API Level 35）平台实现的"长按区域截屏+自由缩放+拖拽分享"一体化工具。

## 功能特性

### 核心功能（P0 - MVP阶段）
- ✅ **长按截屏**: 长按屏幕任意位置触发局部截屏，生成300×300像素正方形区域
- ✅ **自由缩放**: 支持双指缩放调整截取区域，双击复位
- ✅ **拖拽分享**: 重新长按截取区域并拖拽至提示区域，弹出聊天框展示图片
- ✅ **图片保存**: 聊天框关闭后自动保存图片到系统相册
- ✅ **异常处理**: 完善的权限、存储、操作冲突等异常处理

## 技术架构

### 核心类结构
- **MainActivity**: 权限申请与服务开关管理
- **CaptureScreenService**: 核心服务，管理悬浮窗、手势检测、截屏等
- **CaptureRegionView**: 截取区域自定义View（显示、缩放、拖拽）
- **HintView**: 提示词View
- **ChatBottomSheetActivity**: 聊天框展示Activity
- **ScreenCapturer**: 截屏工具类

### 主要技术
- MediaProjection API: 屏幕截取
- GestureDetector/ScaleGestureDetector: 手势检测
- WindowManager: 悬浮窗管理
- BottomSheetBehavior: 聊天框动画

## 开发环境要求
- Android Studio Hedgehog | 2023.1.1+
- JDK 8+
- Android SDK API 35 (Android 15)
- Gradle 8.2

## 使用说明

### 1. 编译与安装
```bash
# 使用Gradle编译
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 2. 应用使用流程
1. 打开应用，点击"开启截屏服务"
2. 授予悬浮窗权限和截屏权限
3. 长按屏幕任意位置触发截屏
4. 双指缩放调整截取区域
5. 重新长按截取区域并拖拽至"拖动图片到此处分享"提示区域
6. 聊天框弹出显示图片，点击底部"×"按钮关闭并保存图片

## 权限说明
- **SYSTEM_ALERT_WINDOW**: 悬浮窗权限（显示截取区域、提示词、聊天框）
- **MediaProjection**: 屏幕截取权限
- **READ_MEDIA_IMAGES / WRITE_EXTERNAL_STORAGE**: 存储权限（保存图片）
- **POST_NOTIFICATIONS**: 通知权限（可选）

## 项目结构
```
CaptureScreen/
├── app/
│   ├── src/main/
│   │   ├── java/com/capturescreen/app/
│   │   │   ├── MainActivity.java
│   │   │   ├── CaptureScreenService.java
│   │   │   ├── CaptureRegionView.java
│   │   │   ├── HintView.java
│   │   │   ├── ChatBottomSheetActivity.java
│   │   │   └── ScreenCapturer.java
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── doc/
│   └── PRD文档.md
└── build.gradle
```

## 后续计划（P1阶段）
- [ ] 图片管理功能（自定义保存路径、格式等）
- [ ] 设置项配置界面
- [ ] 更多自定义选项（边框颜色、提示词样式等）
