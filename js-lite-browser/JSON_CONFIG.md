# JS Lite Browser v1.3 — JSON 設定檔規範

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
    "sidebarEnabled": true,
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
| `developer.sidebarEnabled` | 布林 | 右側快捷抽屜設定；只有開發者模式開啟時才能滑出。 |
| `developer.scripts` | 陣列 | 匯入腳本；未指定則不改變現有腳本。 |
| `appearance` | 物件 | 介面屬性；未給欄位沿用原值。 |

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