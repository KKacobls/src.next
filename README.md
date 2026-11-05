# JS Lite Browser (Android)

This is a standalone Android WebView browser project on an isolated branch of the Kiwi fork. The original `kiwi` default branch is unchanged.

Build via **Actions > Build JS Lite Browser APK**. The workflow restores the exact source archive in `assets/`, builds it with Gradle 8.11.1 / AGP 8.9.2, and uploads the Debug APK artifact.

Features: Traditional Chinese / English, single-page LINE-like UI, URL navigation, manual JavaScript console, browser storage and selected website permissions.

The project source archive is pinned by SHA-256 in the workflow. Debug APK is for personal testing only.
