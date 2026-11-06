# Android APK 固定簽章（從 v1.3.1 開始）

## v1.3.2 最新更新

v1.3.2（`versionCode 10`）已沿用 v1.3.1（`versionCode 9`）建立的相同固定簽章，**安裝過 v1.3.1 的手機可以直接安裝 v1.3.2 覆蓋更新**，不需再次執行 `gh secret set`，也無需移除 App。GitHub Actions 會重新驗證原本的憑證指紋。

## 為什麼原本每次安裝都衝突？

Android 要讓新版 APK **直接覆蓋安裝**，必須使用相同的 `applicationId`、相容的簽章憑證，且新版的 `versionCode` 更高。原本 GitHub Actions 每次產生 Runner 內的 Debug Key，導致不同建置的簽章不一致。

**從 v1.3.1 開始：建立固定 PKCS12 金鑰，並在往後建置使用相同金鑰。**

- `applicationId`：`com.kk.jsbrowser`（不變）
- 首個穩定簽章版本：`v1.3.1`，`versionCode = 9`
- GitHub Actions Secret：`JSLITE_SIGNING_PASSWORD`
- 加密備份：`signing/jslite-release-keystore.p12.enc`
- 憑證指紋：`signing/jslite-release-cert-sha256.txt`
- Release：`js-lite-browser-v1.3.1`

**私鑰不會以明文提交到 GitHub**。首次建置會自動產生 PKCS12、用 AES-256-CBC + PBKDF2 加密備份並儲存於本分支，之後每次透過相同 Secret 解密、檢查指紋，並簽署新的 Release APK。

## 一次性設定（在 Windows PowerShell 執行）

請使用擁有 `KKacobls/src.next` 權限的 GitHub 帳號。

### 1. 安裝 GitHub CLI 並登入

```powershell
winget install --id GitHub.cli -e
gh auth login
```

若已安裝，直接執行 `gh auth login`。安裝後若 PowerShell 找不到 `gh`，重新開啟一個 PowerShell 視窗。

### 2. 安全產生一次性高強度密碼並寫入 GitHub Secret

```powershell
$randomBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($randomBytes)
$rng.Dispose()
$secret = -join ($randomBytes | ForEach-Object { $_.ToString('x2') })
$secret | gh secret set JSLITE_SIGNING_PASSWORD --repo KKacobls/src.next
Remove-Variable secret, randomBytes
```

不要把密碼傳到聊天、Issue 或儲存庫中的文字檔。上述指令將密碼透過標準輸入送給 GitHub CLI；沒有在終端機畫面列出密碼。

**第一次完成後，不要刪除或更改這個 Secret。** 更換密碼而不重新加密 keystore 會讓後續建置停止，並非輪換簽章金鑰的方法。日後若需要輪換加密密碼，應先保留舊密碼並執行正式重新加密程序。

### 3. 手動觸發一次建置

```powershell
gh workflow run build-js-lite-apk.yml --repo KKacobls/src.next --ref js-lite-browser-build
gh run list --repo KKacobls/src.next --workflow build-js-lite-apk.yml --limit 5
```

等待 GitHub Actions 結束，檢查結果為 Success：

[GitHub Actions](https://github.com/KKacobls/src.next/actions/workflows/build-js-lite-apk.yml)

當 `JSLITE_SIGNING_PASSWORD` 未設定，工作流程只會編譯測試，**不會發布新的 Debug APK**，以免又提供不同簽章。

### 4. 安裝

完成第一次穩定簽章建置後，從以下網址下載：

[JS Lite Browser v1.3.1 Release](https://github.com/KKacobls/src.next/releases/tag/js-lite-browser-v1.3.1)

**注意：此前 v1.3.0 等版由臨時 Debug Key 簽章，與 v1.3.1 的固定金鑰不相容。** 第一次從舊版轉移，仍需先將 JS 腳本與設定匯出備份、卸載舊版，再安裝新版本，然後匯入設定。從 v1.3.1 開始，只要固定金鑰與較高 `versionCode` 不變，未來正常更新即可覆蓋，不用反覆卸載。

## 安全防護

- 沒有 Secret：**不發布 APK**。
- 加密備份缺檔或指紋缺檔：**停止建置**，不重新產生憑證。
- 金鑰解密失敗：**停止建置**。
- 憑證指紋不一致：**停止建置**。
- APK 簽署憑證與釘選指紋不同：**停止發布**。
- 新版編譯成功但分支已被其他更新移動：**不發布過時 APK**。
- 備份 `signing/jslite-release-keystore.p12.enc` 與憑證指紋，不要刪除。密碼與加密檔必須共同保留才能維持更新能力。
- 不要把 keystore 的明文、GitHub Secrets 或簽署密碼加到 Release 資產裡。

## 升級之後如何更新版本？

在程式庫提高 `app/build.gradle` 的 `versionCode`（必須增加）與 `versionName`，同步更新 Release 的工作流程檔名／標籤，然後 GitHub Actions 會解密同一把金鑰簽署新版 APK。不要再用 `assembleDebug` 的 APK 當作正式更新包。

`assembleRelease` 沒有有效簽署參數時會拒絕建立可分發版本。
