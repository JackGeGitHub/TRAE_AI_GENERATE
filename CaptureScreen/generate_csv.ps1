
# 定义数据
$data = @(
    "序号,文件名称,相对路径,文件类型,代码行数,功能描述",
    "1,MainActivity.java,app/src/main/java/com/capturescreen/app/MainActivity.java,Java源文件,150,应用主界面，处理权限申请和服务启停",
    "2,CaptureScreenService.java,app/src/main/java/com/capturescreen/app/CaptureScreenService.java,Java源文件,515,核心服务，管理截屏流程、悬浮窗和MediaProjection",
    "3,ScreenCapturer.java,app/src/main/java/com/capturescreen/app/ScreenCapturer.java,Java源文件,121,屏幕捕获工具类，从ImageReader获取图像并裁剪",
    "4,CaptureRegionView.java,app/src/main/java/com/capturescreen/app/CaptureRegionView.java,Java源文件,266,截屏区域视图，支持双指缩放和长按拖拽",
    "5,ChatBottomSheetActivity.java,app/src/main/java/com/capturescreen/app/ChatBottomSheetActivity.java,Java源文件,156,底部弹窗，显示截图并保存到相册",
    "6,HintView.java,app/src/main/java/com/capturescreen/app/HintView.java,Java源文件,91,提示视图，显示拖拽分享提示",
    "7,AndroidManifest.xml,app/src/main/AndroidManifest.xml,配置文件,44,应用清单配置，权限声明和组件注册",
    "8,activity_main.xml,app/src/main/res/layout/activity_main.xml,布局文件,34,主界面布局",
    "9,activity_chat_bottom_sheet.xml,app/src/main/res/layout/activity_chat_bottom_sheet.xml,布局文件,31,底部弹窗布局",
    "10,overlay_view.xml,app/src/main/res/layout/overlay_view.xml,布局文件,5,悬浮窗布局",
    "11,strings.xml,app/src/main/res/values/strings.xml,资源文件,16,字符串资源",
    "12,colors.xml,app/src/main/res/values/colors.xml,资源文件,12,颜色资源",
    "13,themes.xml,app/src/main/res/values/themes.xml,资源文件,14,主题样式",
    "14,build.gradle,app/build.gradle,Gradle配置,38,应用模块Gradle配置",
    "15,build.gradle,build.gradle,Gradle配置,3,项目根Gradle配置",
    "16,settings.gradle,settings.gradle,Gradle配置,23,项目设置配置",
    "17,gradle.properties,gradle.properties,配置文件,3,Gradle属性配置",
    "18,proguard-rules.pro,app/proguard-rules.pro,配置文件,1,ProGuard混淆规则",
    "19,README.md,README.md,文档,0,项目说明文档"
)

# 保存文件
$outputPath = "d:\Workspace\Android\Codes\TRAE_AI_GENERATE\CaptureScreen\doc\CaptureScreenCodes.csv"
$data | Out-File -FilePath $outputPath -Encoding UTF8
Write-Host "CSV文件已成功生成: $outputPath"
Write-Host "该文件可以在Excel中打开查看"
