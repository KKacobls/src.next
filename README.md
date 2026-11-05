# JS Lite Browser (Android)

Standalone Android WebView browser on an isolated branch of the Kiwi fork; the original kiwi branch is untouched.

**v1.1.3 localization repair:** Traditional Chinese is the default Android strings.xml; English resources are in values-en. zh-Hant-TW and zh-TW qualifiers are included explicitly. Users who previously installed v1.0-v1.1.2 get a one-time migration to Traditional Chinese, so old saved System/English preference doesn't silently override the new default. After migration, manual English or Follow system works as intended.

All native browser menu labels and dialogs (including JavaScript alert/confirm buttons, Auto JS scripts, Console, URL/search, Cancel, Confirm) use localized resources. Website content is not translated or restyled. All v1.1.2 UI, version information in ⋮, WebView, manual/automatic JavaScript and URL matching are retained.

APK is published through GitHub Releases. Build uses Java 17 + Gradle 8.11.1 and patches over the pinned project archive.

**Note:** The GitHub Debug APK may use a different signing certificate from prior versions. If Android reports a package conflict, back up JS scripts before uninstalling the old version; uninstall clears browser data.
