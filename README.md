# ScanQrCode
ScanQrCode 可以轻松的实现多个条码的扫码和二维码扫描，并且可以自定义布局，无需手动写代码自己申请权限，一句话实现扫码功能

在项目的build.gradle中添加
```gradle
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```
在使用的module的build.gradle中添加
```gradle
dependencies {
	  implementation 'com.github.songcream:ScanQrCode:1.0.0'
}
```
要使用的Activity继承CaptureActivity，并且实现```kotlin handleScanResult(result: Result?)``` 和 ```kotlin decodeMode()``` 这两个方法
代码如下：
```kotlin
class MainActivity : CaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun handleScanResult(result: Result?) {
        var text=""
        result?.stringList?.forEach {
            text=text+it;
        }
        Toast.makeText(this,text,Toast.LENGTH_LONG).show()
	
	restartPreviewAfterDelay(1000)
    }

    override fun decodeMode(): Int {
        return DecodeThread.ALL_MODE;
    }
}
```

DecodeMode有三种模式：
```kotlin
DecodeThread.BARCODE_MODE = 0X100;        扫条码模式      
DecodeThread.QRCODE_MODE = 0X200;         扫二维码模式
DecodeThread.ALL_MODE = 0X300;            同时识别条码和二维码，两者都可以扫
```
```kotlin
override fun handleScanResult(result: Result?) {}
```
这个方法可以对扫描结果进行处理，返回识别结果列表
```kotlin
result.getStringList()
```  
处理完要调用 ```kotlin restartPreviewAfterDelay(1000)```，进行下一次识别
