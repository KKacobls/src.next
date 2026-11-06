# JS Lite Browser for Android — v1.3.2

This is the dedicated **JS Lite Browser** branch of `KKacobls/src.next`. It builds a standalone Android WebView browser, not the massive Kiwi Chromium source tree. The Kiwi default branch remains unchanged.

## 新版功能

- 保留 v1.1.4 的左上角網頁標題與網域外觀，點擊標題直接進入網址／搜尋框。
- 右上角 `⋯`：**3×3 純圖示九宮格（沒有文字標籤）**，九項操作仍保留無障礙標籤。
- 瀏覽歷史、單一最近關閉分頁、**非阻擋式頁內搜尋列（類 Chrome Ctrl+F；可在搜尋時操作網頁）**。
- 下載紀錄：狀態、百分比、檔案名稱、時間、開啟、取消、刪除紀錄。
- 外部 App 跳轉確認：來源網址、目的地網址、目標 App、套件名稱與圖示（若系統可解析），可記住選擇。
- 自訂 Adaptive Launcher Icon。
- 版本號連按 7 下啟用永久開發者模式（不以中途 Toast 阻擋操作）。
- 開發者專用 **JS 側邊工具面板**：從 `⋯ → 開發者工具 → 開啟 JS 側邊工具` 點選，不使用滑動手勢。面板內可直接輸入 JS，按「執行 JS」在目前網頁執行；留在面板外的網頁仍可操作。
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

**經過固定簽章驗證的更新 APK**：請至 GitHub Releases 中下載 `js-lite-browser-v1.3.2`。憑證沿用 v1.3.1，若手機目前是 v1.3.1 可直接覆蓋安裝，無須卸載。

`gradle assembleDebug` 僅作開發與編譯測試；正式更新使用 `assembleRelease` 和固定金鑰。打包成功不代表已在實體 Android 手機測試。

## 邊界與限制

JS Console 使用 WebView evaluateJavascript，不等同完整 Chromium DevTools；自動 JS 不含所有 Tampermonkey GM_* API。下載由 Android DownloadManager 管理。v1.3.2 不再提示設定預設瀏覽器，也不再顯示權限捷徑；外部 App 名稱與圖示依 Android 套件可見性而定。