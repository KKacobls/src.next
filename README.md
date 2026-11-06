# JS Lite Browser for Android — v1.3.0

This is the dedicated **JS Lite Browser** branch of `KKacobls/src.next`. It builds a standalone Android WebView browser, not the massive Kiwi Chromium source tree. The Kiwi default branch remains unchanged.

## 新版功能

- 保留 v1.1.4 的左上角網頁標題與網域外觀，點擊標題直接進入網址／搜尋框。
- 右上角 `⋯` 內的九宮格：網址、重新整理、返回、首頁、分享、歷史、縮小、放大、電腦版／手機版。
- 瀏覽歷史、單一最近關閉分頁、頁內搜尋（類 Ctrl+F）。
- 下載紀錄：狀態、百分比、檔案名稱、時間、開啟、取消、刪除紀錄。
- 首次啟動詢問是否設定預設瀏覽器，亦可之後從選單開啟 Android 系統設定。
- 外部 App 跳轉確認：來源網址、目的地網址、目標 App、套件名稱與圖示（若系統可解析），可記住選擇。
- 自訂 Adaptive Launcher Icon。
- 版本號連按 7 下啟用永久開發者模式（不以中途 Toast 阻擋操作）。
- 開發者專用右側快捷抽屜：需要開發者模式且單獨啟用快捷抽屜，才能由螢幕右緣向左滑動開啟。
- 自動 JS 管理、JS 主控台、目前 DOM HTML 原始碼檢視、可視化介面風格、可調整 HSV 顏色。
- 內建十種帶預覽的風格選項，並可儲存、修改名稱、刪除自訂樣式。
- JSON 匯入匯出支援 `browser.startUrl`、`developer.scripts`，使用與原生管理頁面相同的腳本存放格式。
- **腳本儲存模式可選擇**：`persist: true` 永久保存、`persist: false` 僅本次 App 執行有效；`enabled` 另行控制是否執行。

## JSON 規範

完整說明及示例請看 [JSON_CONFIG.md](js-lite-browser/JSON_CONFIG.md)。

## Build

`js-lite-browser-build` 分支的 GitHub Actions 使用 Java 17、Android SDK 35、Gradle 8.11.1：

```sh
cd js-lite-browser
gradle assembleDebug --no-daemon
```

下載已編譯 APK：GitHub Releases 中的 `js-lite-browser-v1.3.0`。

此為 **debug APK**，有簽章衝突時請先備份舊版自動 JS 設定再處理安裝；打包成功不等於已經在實體手機完成操作測試。

## 邊界與限制

JS Console 使用 WebView evaluateJavascript，不等同完整 Chromium DevTools；自動 JS 不含所有 Tampermonkey GM_* API。下載由 Android DownloadManager 管理。預設瀏覽器身分需經 Android 系統確認；外部 App 名稱與圖示依 Android 套件可見性而定。