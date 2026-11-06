# KK瀏覽器・油猴測試版 (GeckoView + 原版 Tampermonkey)

這是**獨立的 Android APK 測試版**。原本的 KK瀏覽器（Android WebView）與所有資料不會被修改。

## 技術架構

- App 套件名稱：`com.kk.kkmonkey`（與 `com.kk.kkbrowser`、`com.kk.jsbrowser` 都不同）。
- 瀏覽引擎：Mozilla GeckoView 143，直接內嵌 Firefox 的網頁與 WebExtensions 執行引擎。
- 擴充套件：**真正的 Tampermonkey**，而不是自行仿製的 JavaScript 執行器。
- 沒有把 Tampermonkey 的 CRX、XPI 或私有原始碼放進 APK。
- 使用者點擊 **安裝油猴**，App 會從 Mozilla AMO 官方 API
  `https://addons.mozilla.org/api/v5/addons/addon/tampermonkey/`
  取得官方下載 URL，只允許 `https://addons.mozilla.org/.../*.xpi`。
- GeckoView 的 `WebExtensionController.install()` 會檢查 Mozilla 簽章與套件格式。
- 安裝時彈出擴充套件所需權限清單，使用者可以同意或拒絕。
- 安裝成功後，擴充套件會隨 GeckoRuntime 長期保存，不需每次啟動重裝。

## 手機操作

1. 安裝 **KK瀏覽器・油猴版**。
2. App 開啟後，點上方 **🐵 安裝油猴** → **從 Mozilla 安裝**。
3. 在顯示 Tampermonkey 權限清單的對話框按 **同意安裝**。
4. 安裝完成後點 **🐵 油猴已安裝**，可選擇：
   - **開啟油猴選單**：觸發擴充套件原本的 browser action / popup。
   - **開啟腳本管理介面**：以 GeckoView 新分頁開啟擴充套件 `optionsPageUrl`。
   - **檢查擴充套件更新**：呼叫官方 GeckoView extension update。
   - **移除油猴**：解除擴充套件與其資料。
5. 測試開啟 `https://example.com/`，將以下 `.user.js` 匯入原版 Tampermonkey。
   [範例油猴腳本](examples/verify-tampermonkey.user.js)
6. 重新整理 `https://example.com/`。正常情況下會看到綠色的「Tampermonkey 已自動執行」橫幅。

## 與原本的 KK瀏覽器有什麼不同？

這個版本是 **GeckoView 原型測試**。目前只有網址列、上一頁、重新整理、分頁切換、原版油猴安裝/管理與基本提示。之前 KK瀏覽器的 CSS 介面風格、九宮格、下載管理、自製 JS 側邊欄尚未搬過來，目的是**先確認真正的油猴能用**。

Firefox/Tampermonkey 的擴充套件 API 會因瀏覽器嵌入環境而有差異。**GitHub Actions APK 編譯成功不代表已通過 Android 真機腳本執行測試。** 需實際按「安裝油猴」、開啟 Dashboard、安裝範例腳本並確認頁面自動執行；若中途失敗，請提供錯誤截圖或訊息，以便逐項修正，不會宣稱已測成功。

## 編譯

所需環境：Java 17、Android SDK 35、Gradle 8.11.1、Mozilla Maven Repository。

```bash
gradle -p kk-gecko-browser assembleDebug
```

固定簽章版透過：
[build-kk-gecko-tampermonkey.yml](../.github/workflows/build-kk-gecko-tampermonkey.yml)

GitHub Actions 從既有的 `signing/jslite-release-keystore.p12.enc` 解密同一把金鑰，與憑證指紋比對，成功後發布 `KK-Gecko-Tampermonkey-v0.1.0.apk`。

## 安全

Tampermonkey 的網站權限很廣，安裝時要確認來源與權限。請只匯入信任來源的油猴腳本；它們可能讀取或修改網頁內容。此測試版不會在沒有你的確認下安裝擴充套件。
