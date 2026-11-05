# JS Lite Browser (Android)

Standalone Android WebView browser on an isolated branch of the Kiwi fork; the original kiwi branch is untouched.

**Latest v1.1.1:** The normal LINE-style browsing header has no JS button. Tap the title/domain to open the URL editing dialog: there you can edit the URL, toggle **iPhone / Android** header layout, or tap **JS** to manage URL-matched auto-run JavaScript scripts. Long press the header for other tools, including a manual Console. New installs default to Traditional Chinese; English and system language remain selectable.

Download the Android Debug APK from **GitHub Releases** (no ChatGPT/sandbox links or Actions ZIP needed). GitHub Actions builds from the pinned archive plus versioned patches, with Java 17 and Gradle 8.11.1.

**Signing:** GitHub-hosted ephemeral debug signing keys may change across builds. If Android reports a package signature conflict, uninstall the previous app before installing (this deletes app data). For seamless upgrades later, configure a secure and stable release signing key using GitHub Secrets.
