# AMARadio

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0.html)
<a href='https://play.google.com/store/apps/details?id=com.ounben.amaradio'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height='60px'/></a>

**AMARadio** is an open-source Android application for worldwide radio streaming. By leveraging the community-supported [radio-browser.info](https://www.radio-browser.info/) database, AMARadio provides instant access to thousands of stations in a stable, modern, and user-friendly environment.

## Core Philosophy

AMARadio was developed to enable an honest and distraction-free listening experience. The app is **completely ad-free, contains no tracking, and no unnecessary bloatware**. Originally started as a personal project to create a simple and accessible interface for global radio, the app remains consistently transparent and easy to use – for everyone.

## Key Features

- **Instantaneous UI**: Thanks to a highly optimized architecture, switching between tabs and opening the player occurs without delay or flickering.
- **Advanced Android Auto Integration**: 
    - **Stability (Media-Anchor)**: Specialized logic to prevent session disconnects during station switches on modern Android versions (15+).
    - **HLS/TS Support**: Full compatibility with complex Transport Stream formats.
    - **Optimized UI**: Compact list designs, driver-safe folder hierarchy, and prioritized metadata (Station Name first) for maximum legibility.
- **Modern Home Screen Widgets**: Feature-rich widgets based on **Jetpack Glance**. 
    - **Instant Sync**: Real-time updates for Favorites and History using a high-performance Push-model.
    - **Live Track Info**: Displays current Artist and Song Title directly on the Home Screen.
    - **Themed Previews**: Professional, adaptive previews in the system widget picker.
- **Comprehensive Accessibility**: Fully optimized for **TalkBack** and screen readers. Features semantic grouping of information, localized accessibility strings in over 74 languages, and "speaking" status icons for fluid navigation.
- **Advanced Search & Filtering**: Find stations by Name, Country, Language, or Tags. Metadata for over **11,000 tags** is cached locally in a high-performance SQL database to enable instant offline suggestions.
- **Community Support (Click Counting)**: Supports the global ranking of radio stations by reporting playback clicks directly to the official radio-browser.info API.
- **Dynamic UI Scaling**: Custom settings allow for the adjustment of the user interface size from Compact to Extra Large.
- **Broad Language Support**: Support for over **74 languages**, including a variety of African languages (Afrikaans, Amharic, Swahili, Zulu) with a dedicated in-app language selector.
- **Modern Design**: Developed with **Jetpack Compose**, featuring seamless support for Dark/Light modes and edge-to-edge system integration.
- **High-Performance Streaming**: A robust engine based on **AndroidX Media3 (ExoPlayer)** with real-time audio thread prioritization, strict audio focus handling, and intelligent connection management.
- **High Availability**: Integrated failover support via mirror servers ensures uninterrupted station browsing even if the primary database is offline.
- **Smart Management**: Includes a sleep timer, efficient favorites management, and full support for M3U playlist export/import.
- **Robust Data Resilience**: Features a unique **Dual-Database Architecture** that strictly separates the global radio catalog from personal user data (Favorites, History, Filters). This ensures your personal settings are never lost during global database updates.

## Screenshots

<p align="center">
  <img src="screenshots/AMARadio_Playstore (25).png" width="24%" />
  <img src="screenshots/AMARadio_Playstore (26).png" width="24%" />
  <img src="screenshots/AMARadio_Playstore (27).png" width="24%" />
  <img src="screenshots/AMARadio_Playstore (28).png" width="24%" />
</p>
<p align="center">
  <img src="screenshots/AMARadio_Playstore (29).png" width="24%" />
  <img src="screenshots/AMARadio_Playstore (30).png" width="24%" />
  <img src="screenshots/AMARadio_Playstore (31).png" width="24%" />
  <img src="screenshots/AMARadio_Playstore (32).png" width="24%" />
</p>
<p align="center">
  <img src="screenshots/AMARadio_Playstore (33).png" width="24%" />
</p>

## Getting Started

### Installation
The latest version introduces a high-performance **Dual-Database Architecture**, SQL metadata caching, and official click count support. You can download the application from the [Google Play Store](https://play.google.com/store/apps/details?id=com.ounben.amaradio) or the GitHub releases page.

### Building from Source
To build the project locally, ensure you have the latest version of Android Studio installed.

1. Clone the repository:
   ```bash
   git clone https://github.com/ounben/AMARadio.git
   ```
2. Open the project in **Android Studio**.
3. Build using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```

## Technical Specification

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose & Material 3
- **Architecture**: MVVM with Activity-scoped ViewModels for state management
- **Persistence**: 
    - **Station Catalog**: High-performance SQL for the worldwide radio database.
    - **User Storage**: Dedicated SQL-based **UserDatabase** for Favorites, History, and custom Filters (migrated from legacy JSON).
- **Networking**: OkHttp 5 & Coroutines for API communication, Coil for async image loading
- **Media Engine**: AndroidX Media3 (ExoPlayer)
- **Target SDK**: 37 (Android 17)
- **Minimum SDK**: 26 (Android 8.0)

## Contributing

Contributions are welcome and appreciated. If you wish to improve the codebase, fix bugs, or update translations, please follow these steps:
- Report issues via the [GitHub Issue Tracker](https://github.com/ounben/AMARadio/issues).
- Submit improvements via Pull Requests.
- Translation updates are highly encouraged to improve global accessibility.

## License

This project is licensed under the **GNU General Public License v3.0**. Detailed information can be found in the [LICENSE](LICENSE) file.

---
<p align="center">
  AMARadio - Professional Radio Streaming for Android.
</p>
