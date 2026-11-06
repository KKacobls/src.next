# KK瀏覽器 for Android — v1.3.3

這是 KK瀏覽器 Android WebView App，GitHub 分支保留為 `js-lite-browser-build`，原本 Kiwi/其他程式碼不受影響。

## 安裝衝突修正：從 v1.3.3 起使用獨立 App 身分

- **KK瀏覽器**：`com.kk.kkbrowser`（新套件，`versionCode 11`）。
- **舊 JS 極簡瀏覽器**：`com.kk.jsbrowser`。兩者可同時安裝，避免碰到舊 App 的簽章衝突。
- 舊 App 的 Cookie、歷史紀錄、資料庫等不會自動轉移。請在舊版匯出 JSON（腳本、設定），再在 KK瀏覽器的**懶人模式**匯入。無法透過 JSON 匯出 Cookie。
- 固定簽章加密備份與已釘選的 SHA-256 憑證不變；未來 KK瀏覽器版本必須繼續使用 `com.kk.kkbrowser` + 同簽章 + 更高 `versionCode`，才可覆蓋更新。
- 已裝 v1.3.1 / v1.3.2 的使用者若安裝 v1.3.3，會看到**另一個獨立 App**，這是刻意的套件遷移，不是原地覆蓋升級。

## 懶人模式（JSON 直接套用）

從右上角 `⋯ → 懶人模式（JSON 匯入／匯出）` 貼上 JSON。支援最簡單的 `url + js` 寫法，會自動建立匹配該網址網域的 JS 腳本，啟用自動 JS，並前往該網址；`persist: true` 永久保存，`persist: false` 僅本次 App 程序有效。

[一鍵複製範例](js-lite-browser/examples/lazy-auto-js.json)｜[完整 JSON 說明](js-lite-browser/JSON_CONFIG.md)。

## 新版功能

- 保留 v1.1.4 的左上角網頁標題與網域外觀，點擊標題直接進入網址／搜尋框。
- 右上角 `⋯`：**3×3 純圖示九宮格（沒有文字標籤）**，九項操作仍保留無障礙標籤。
- 瀏覽歷史、單一最近關閉分頁、**非阻擋式頁內搜尋列（類 Chrome Ctrl+F；可在搜尋時操作網頁）**。
- 下載紀錄：狀態、百分比、檔案名稱、時間、開啟、取消、刪除紀錄。
- 外部 App 跳轉確認：來源網址、目的地網址、目標 App、套件名稱與圖示（若系統可解析），可記住選擇。
- 自訂 Adaptive Launcher Icon。
- 版本號連按 7 下啟用永久開發者模式（不以中途 Toast 阻擋操作）。
- 開發者專用 **側邊開發者工具**：從 `⋯ → 開發者工具 → 開啟側邊開發者工具` 點選，不使用滑動手勢。內含 JS 即時執行、Console、自動腳本、原始碼、CSS、風格、工具列位置及 JSON；仍可操作沒有被面板覆蓋的網頁。
- 自動 JS 管理、JS 主控台、目前 DOM HTML 原始碼檢視、可視化介面風格、可調整 HSV 顏色。
- 內建十種帶預覽的風格選項，並可儲存、修改名稱、刪除自訂樣式。
- JSON 匯入匯出支援 `browser.startUrl`、`developer.scripts`，使用與原生管理頁面相同的腳本存放格式。
- **腳本儲存模式可選擇**：`persist: true` 永久保存、`persist: false` 僅本次 App 執行有效；`enabled` 另行控制是否執行。

## JSON 規範

完整說明及示例請看 [JSON_CONFIG.md](js-lite-browser/JSON_CONFIG.md)。

## 固定 APK 簽章（自 v1.3.1 開始）

從 v1.3.1 起，正式提供給使用者的 APK 必須以同一個 PKCS12 憑證簽署，並由 GitHub Actions 保留加密 Keystore、驗證憑證 SHA-256 指紋。**必須一次性設定** GitHub Repository Secret `JSLITE_SIGNING_PASSWORD`，無密碼時只編譯測試，不發布新 APK。請依照 [SIGNING.md](js-lite-browser/SIGNING.md) 完成設定。

舊的 v1.3.0 及更早 Debug APK 可能不相容，第一次轉換建議備份後卸載一次；完成固定簽章後，新版只要 `versionCode` 更高即可持續覆蓋更新。不要重新產生新的簽章。

## Build

`js-lite-browser-build` 分支的 GitHub Actions 使用 Java 17、Android SDK 35、Gradle 8.11.1：

```sh
cd js-lite-browser
gradle assembleDebug --no-daemon
```

**已固定簽章的 KK瀏覽器 APK**：請至 GitHub Releases 下載 `kk-browser-v1.3.3`。此版本使用**新套件 ID**，無須先卸載舊 JS 極簡瀏覽器，但新舊資料互不共用。

`gradle assembleDebug` 僅作開發與編譯測試；正式更新使用 `assembleRelease` 和固定金鑰。打包成功不代表已在實體 Android 手機測試。

## 邊界與限制

JS Console 使用 WebView evaluateJavascript，不等同完整 Chromium DevTools；自動 JS 不含所有 Tampermonkey GM_* API。下載由 Android DownloadManager 管理。v1.3.2 不再提示設定預設瀏覽器，也不再顯示權限捷徑；外部 App 名稱與圖示依 Android 套件可見性而定。