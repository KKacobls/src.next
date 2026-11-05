# JS Lite Browser (Android)

Standalone Android WebView browser on an isolated branch of the Kiwi fork; the original kiwi branch is untouched.

**v1.1.2:** A clean LINE-style top header shows webpage title, domain, ⋮ overflow menu and close ×. The rounded overflow menu includes URL/search, refresh, back, home, zoom-out, share, app permissions, auto-JS scripts, manual JS console, iPhone/Android header style, language, and clear browsing data. Version number appears at the bottom of the menu and in URL edit. No tabs.

The native UI uses Android sans-serif typography with dark heading text and muted-grey descriptions. WebView JavaScript alert/confirm dialogs use a centered bold domain, soft grey text and green confirm action. **Website CSS is untouched.** Defaults to Traditional Chinese.

Download APK from **GitHub Releases**. GitHub Actions restores pinned source and versioned patches with Java 17 and Gradle 8.11.1.

**Signing:** GitHub ephemeral debug keys might change between builds. Android may require uninstalling earlier versions (which deletes app data and saved user scripts). For seamless upgrade, configure permanent secure signing keys via GitHub Secrets.
