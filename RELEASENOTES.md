# Release 0.88 - The "Mother's Promise" Update 📻❤️

This is a major milestone for **AMARadio**. This update not only brings modern technical standards but also focuses on accessibility and our core philosophy: a clean, honest, and 100% ad-free radio experience.

### 🌟 Highlights

*   **100% Kotlin Migration**: The entire codebase has been migrated from Java to Kotlin. This ensures better performance, modern development standards, and long-term maintainability for future Android versions.
*   **A Personal Promise**: AMARadio was originally developed for my mother to provide a simple radio experience without annoying ads. This release reaffirms that promise—AMARadio is and will always remain free of ads and bloatware.
*   **Target SDK 37**: Fully optimized for the latest Android 14 and 15+ features and security standards.

### ✨ New Features

*   **Advanced Filtering**: A dedicated new "Filter" tab. Search for stations by:
    *   **Country**: Includes flags and full country names.
    *   **Language**: Find content in your preferred tongue.
    *   **Tags**: Improved selection with AutoComplete (starts after the first character).
    *   *Note: Your filter settings are now saved and restored when you restart the app!*
*   **Dynamic UI Scaling**: Improved accessibility settings. You can now adjust the UI size from "Compact" to "Very Large." This scales not just text, but also buttons and icons for better usability.
*   **Global Filter Access**: A new filter icon in the top toolbar lets you jump to your search settings from anywhere in the app.

### 🎨 UI & UX Enhancements

*   **Modern Splash Screen**: A new black startup screen that eliminates the "white flash" and displays our privacy promise.
*   **Intuitive Controls**: 
    *   Tapping a station image now directly starts the stream.
    *   The star icon now exclusively handles favorites to prevent accidental removals.
    *   Redesigned filter fields with "Clear" (X) buttons and meaningful icons.
*   **Visual Stability**: Added placeholder icons for stations without logos to ensure a consistent list layout.
*   **Settings Refresh**: Updated the settings icon to a standard "gear" symbol and improved cursor visibility in dark mode using the accent color.

### 🛠 Bug Fixes

*   **Connectivity**: Fixed a loop issue where "Metered Connection" warnings would repeatedly appear on mobile data.
*   **Layout**: Resolved an issue where the bottom player was partially covered by the system navigation bar or overlapping in high scaling modes.
*   **Settings**: Fixed the "About AMARadio" link which was previously non-responsive.
*   **CI/CD**: Fixed resource naming issues (lowercase enforcement) to ensure compatibility with Linux-based GitHub Actions.

---

**Full Changelog**: [v0.86...v0.88](https://github.com/ounben/AMARadio/compare/v0.86...v0.88)

*Made with ❤️ for Radio Lovers.*
