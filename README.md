# OldTV Revival

OldTV Revival 是面向舊款智慧電視的 Android TV 離線辦公與檔案管理工具。第一個實機目標：HERAN HD-50UDF26（Android 5.0.1、2GB RAM）。

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

## 版本狀態
26.4.3 開發分支以 26.4.2 為基礎。此版本移除非必要的檔案搜尋入口與搜尋程式，保留內部儲存瀏覽及 USB 資料夾瀏覽、建立、重新命名與刪除；主畫面以更醒目的橘色大型卡片呈現 PowerPoint 入口。保留 Word、Excel、Writer、PowerPoint 縮圖／全螢幕與最近文件功能。版本尚待建置及實機驗收。
