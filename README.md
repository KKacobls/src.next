# JS Lite Browser (Android)

Standalone Android WebView browser on a dedicated branch of the Kiwi fork. No changes to Kiwi's default branch.

v1.1.4: Fixed JavaScript confirm/alert dialogs using a white 18dp rounded background on a transparent Android dialog window. The bottom Cancel and Confirm actions each occupy exactly 50% of the full dialog width; alert dialogs have one full-width Confirm action. Text remains Traditional Chinese by default, with English available as an option.

All features from v1.1.3 are preserved: LINE-like header, overflow tools and version text, manual JS console, automatic URL-matched JS scripts, and storage.

Download the compiled APK from GitHub Releases. APK is a personal testing debug build and can have a different signature than older versions, which may require removing the previous installation (back up stored scripts first).
