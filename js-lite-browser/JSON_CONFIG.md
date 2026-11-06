# KK瀏覽器 v1.3.3 — 懶人模式／JSON 設定規範

## 懶人模式：只填 url 和 js 就能自動執行

主選單「⋯ → 懶人模式（JSON 匯入／匯出）」提供直接貼上 JSON 並套用的入口。**介面內不會預先塞範例，也沒有模板提示**。例如：

```json
{
  "url": "https://example.com/",
  "name": "進站自動加上藍色邊框",
  "js": "document.body.style.border = '8px solid #2563eb'; console.log('KK瀏覽器：自動 JS 已執行');",
  "enabled": true,
  "persist": true
}
```

匯入後會自動完成以下動作：依 `url` 取得網域 `example.com` → 自動轉為 `*://example.com/*` 匹配規則 → 建立或更新指定名稱的 JS 腳本 → 啟用開發者模式與自動 JS 功能 → 開啟網址。**網站載入完成時**自動執行 `js`。刷新網頁或下次再次造訪同一網域也會自動執行（若 `persist: true`）。不會在不相關網域執行。

欄位：
- `url`：完整 HTTP(S) 網址，匯入後會立即開啟，且作為下次啟動網址。
- `js` 或 `code`：任意 JavaScript 程式碼字串。
- `name`：腳本名稱；同名匯入會更新原腳本，預設「懶人模式腳本」。
- `match`：可省略；省略時會從 `url` 推導匹配網域；若要限制路徑可填 `https://example.com/specific/*`。
- `enabled`：`true` 會自動執行，`false` 僅建立腳本。
- `persist`：`true` 永久儲存，`false` 僅本次 App 程序有效。

`persist` **不是**自動執行開關。腳本只有在 `enabled: true` 且自動 JS 總開關啟用、網址符合時才會執行。

完整格式 `browser.startUrl` 與 `developer.scripts` 也支援；懶人模式同樣支援頂層 `scripts` 陣列，並與完整格式合併。JSON 完全由使用者手動貼上，App 不從網際網路自動下載腳本。

完整範例檔：[examples/lazy-auto-js.json](examples/lazy-auto-js.json)。

## 基本格式

設定入口：瀏覽器的「⋯ → JSON 設定匯入／匯出」。貼上 JSON 後按「匯入」；按「匯出」會複製目前的設定到剪貼簿。
本頁是完整格式說明，**App 內的懶人模式不展示模板提示**。

```json
{
  "schemaVersion": 1,
  "developerMode": true,
  "browser": {
    "startUrl": "https://abc.com/",
    "desktopMode": false,
    "defaultZoom": 100
  },
  "developer": {
    "autoScriptsEnabled": true,
    "scripts": [
      {
        "name": "C",
        "match": "abc.com*",
        "code": "alert('test')",
        "enabled": true,
        "persist": true,
        "runAt": "document-end"
      },
      {
        "name": "只在本次執行有效",
        "match": "https://abc.com/*",
        "code": "console.log('本次執行')",
        "enabled": true,
        "persist": false
      }
    ]
  },
  "appearance": {
    "backgroundColor": "#FFFFFF",
    "textColor": "#141614",
    "toolbarColor": "#E5E6E9",
    "toolbarPosition": "top",
    "fontSize": 16,
    "fontWeight": 600,
    "lineHeight": 1.2,
    "borderRadius": 18,
    "spacing": 8,
    "borderWidth": 0,
    "shadow": 8,
    "toolbarHeight": 62,
    "iconSize": 25
  }
}
```

## 欄位定義

| JSON 欄位 | 型別 | 意義 |
| --- | --- | --- |
| `schemaVersion` | 整數 | 目前僅支援 1；可省略。 |
| `developerMode` | 布林 | 啟用/停用開發者模式。 |
| `browser.startUrl` | 字串 | HTTP(S) 起始網址。匯入後立即前往，下次啟動也會開啟；空字串使用原首頁。 |
| `browser.defaultZoom` | 整數 | 預設縮放百分比，50–200。 |
| `browser.desktopMode` | 布林 | `true` 電腦版；`false` 手機版。 |
| `developer.autoScriptsEnabled` | 布林 | 自動 JS 總開關，與個別 `enabled` 分開。 |
| `developer.scripts` | 陣列 | 匯入腳本；未指定則不改變現有腳本。 |
| `appearance` | 物件 | 介面屬性；未給欄位沿用原值。 |

## 手動 JS 側邊工具（與自動腳本分開）

從 `⋯ → 開發者工具 → 開啟 JS 側邊工具` 點選開啟。**不用邊緣滑動，也沒有 JSON 開關。** 面板的 JavaScript 輸入框按「執行 JS」才會在目前網頁執行，不能自行永久自動執行。最近一次手動輸入並執行的程式碼會在本機暫存，以便再次修改；自動腳本的 `persist`、`enabled` 仍只由腳本管理器／JSON 的 `developer.scripts` 控制。匯入舊版 JSON 的 `sidebarEnabled` 欄位會被忽略，保留其他設定。

## 腳本欄位

| 欄位 | 型別 | 預設 | 說明 |
| --- | --- | --- | --- |
| `name` | 字串 | 必填 | 腳本名稱。同名（不區分英文大小寫）匯入時更新原腳本。 |
| `match` 或 `matches` | 字串 | 必填 | 網址匹配規則。多個條件以換行分隔。 |
| `code` | 字串 | 必填 | 原始 JavaScript 程式碼。 |
| `enabled` | 布林 | `true` | `false` 則儲存但不自動執行。 |
| `persist` | 布林 | `true` | **儲存模式：`true` 永久保留；`false` 僅本次 App 處理程序有效，重新啟動後不保留。** |
| `runAt` | 字串 | `document-end` | 目前僅支援 `document-end`。 |

`persist` 和 `enabled` 是不同概念。例如 `persist: true, enabled: false` 代表永久保留但不執行；`persist: false, enabled: true` 代表只在本次啟動執行。也可在自動 JS 腳本管理頁面修改儲存模式。

### 網址規則

- `abc.com*` 會正規化為 `*://abc.com/*`，只匹配該網域，不匹配 `abc.com.attacker.invalid`。
- `https://abc.com/*` 只匹配 HTTPS 網站。
- `*://*.example.com/*` 同時匹配主網域及其子網域。
- 網址規則使用整個 URL 匹配，不是任意子字串搜尋。
- 腳本在符合網址的頁面載入結束後執行；單頁應用變更路徑時會再比對一次。

### 匯入更新規則

- 舊腳本與新匯入的**同名腳本**合併更新，不重複新增。
- 未出現在 JSON 中的原腳本會保留，不會自動刪除。
- 一次最多匯入 100 個腳本，總數最多 100 個。
- 匯入前先驗證名稱、程式碼長度、網址規則、執行時機及設定型別；有錯誤不應寫入任何新設定。
- 自動 JS 由使用者自己撰寫與授權；惡意網站腳本可能讀取頁面內容，請避免匯入不明來源。
- 匯出 JSON 會同時包含永久與工作階段腳本，並保留各自的 `persist` 欄位；**匯出的檔案本身若被再次匯入，仍會依欄位設定保存**。
- 目前沒有 Tampermonkey 所有 GM_* API 的完整支援；這裡是本地 WebView 自動 JS。
- `appearance` 中部分裝飾屬性目前可能僅儲存/提供編輯，並非所有 Android 元件都採用 CSS 引擎，因此不是完整 CSS 標準實作。

## 可直接複製的單腳本範例

```json
{
  "schemaVersion": 1,
  "browser": {"startUrl": "https://abc.com/"},
  "developer": {
    "scripts": [
      {"name":"C","match":"abc.com*","code":"alert('test')","enabled":true,"persist":false}
    ]
  }
}
```

這個範例的 C 腳本**只在本次執行有效**；如果你要永久保留，請將 `persist` 改成 `true`。