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
26.4.2 開發分支以已驗收的 26.4.1 為基礎。此版本規劃整合檔案搜尋、USB 檔案管理與內部儲存瀏覽改善，並保留 Word、Excel、Writer、PowerPoint、最近文件等既有功能。檔案搜尋與 USB 管理第一階段已提交；正式 APK 建置及實機驗收進行中。
