# OldTV Revival

OldTV Revival 是面向舊款智慧電視的 Android TV 辦公套件原型。第一個實機目標：HERAN HD-50UDF26（Android 5.0.1、2GB RAM；CPU ABI 以實機檢測為準）。

## 開發環境
- JDK 17
- Android SDK Platform 35（編譯用）
- Android 最低版本 API 21（Android 5.0）
- Kotlin + 原生 Android Views
- Gradle 8.9 / Android Gradle Plugin 8.7.3

## 建置
```bash
gradle assembleDebug
```
APK：`app/build/outputs/apk/debug/app-debug.apk`

## 狀態
目前為 26.4.1 開發中：TV 首頁、裝置資訊檢測入口及 GitHub Actions APK 建置。文件編輯引擎尚未實作。
