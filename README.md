# AMARadio

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0.html)

**AMARadio** is a professional, open-source Android application designed for streaming radio stations globally. It leverages the community-driven [radio-browser.info](https://www.radio-browser.info/) database to provide access to thousands of stations.

This project is a refined fork of RadioDroid, optimized for a more focused, accessible, and stable user experience. The codebase has been completely migrated to **100% Kotlin**, adopting modern Android standards and ensuring full compatibility with Android 14 and 15.

## Core Philosophy

**Ad-Free and Privacy-Focused**: AMARadio was developed with the primary goal of providing a clean, honest radio experience. It contains no advertisements, no tracking, and no bloatware. This commitment to a distraction-free environment is a core principle of the project.

## Key Features

- **Advanced Search and Filtering**: A dedicated filtering system allows users to find stations by Name, Country, Language, and Tags. Search results are prioritized using a weighted scoring system for maximum relevance.
- **Dynamic UI Scaling**: Built-in accessibility settings allow for dynamic adjustment of the user interface, including font and component sizes, ranging from Compact to Extra Large.
- **Modern Material Design**: A clean, streamlined interface with full support for Dark and Light modes, featuring the "El Gato" branding and edge-to-edge system integration.
- **Global Station Database**: Instant access to a massive, community-maintained directory of international radio stations.
- **Chromecast Integration**: Seamlessly stream content to compatible TVs and smart speakers.
- **Optimized Performance**: High-stability streaming engine based on AndroidX Media3 (ExoPlayer) with intelligent connection management and metered data warnings.
- **Favorites and History**: Efficient management of preferred stations and playback history with support for M3U playlist export/import.
- **Alarm and Sleep Timer**: Integrated radio alarm clock and sleep timer functionality.
- **Android TV Support**: Specialized user interface optimized for television and large-screen devices.

## Screenshots

<p align="center">
  <img src="screenshots/Screenshot_20260609_021827.png" width="24%" />
  <img src="screenshots/Screenshot_20260609_022031.png" width="24%" />
  <img src="screenshots/Screenshot_20260609_022052.png" width="24%" />
  <img src="screenshots/Screenshot_20260609_022112.png" width="24%" />
</p>
<p align="center">
  <img src="screenshots/Screenshot_20260609_022219.png" width="24%" />
  <img src="screenshots/Screenshot_20260609_022236.png" width="24%" />
  <img src="screenshots/Screenshot_20260609_022322.png" width="24%" />
</p>

## Getting Started

### Installation
The latest version (v0.96) introduces significant stability improvements and branding updates. You can download the application from the releases page or your preferred app store.

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
- **Architecture**: MVVM with Jetpack Components (Lifecycle, ViewModel, ViewBinding)
- **Persistence**: Room Database for metadata, SharedPreferences for configuration
- **Networking**: OkHttp3 for API communication, Coil for asynchronous image loading
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
  AMARadio - Professional Radio Streaming for Android
</p>
