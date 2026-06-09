# AMARadio 📻

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0.html)
[![F-Droid](https://img.shields.io/f-droid/v/net.ounben.AMARadio.svg)](https://f-droid.org/repository/browse/?fdid=net.ounben.AMARadio)
[![Google Play](https://img.shields.io/badge/Google%20Play-Get%20it-green.svg)](https://play.google.com/store/apps/details?id=net.ounben.AMARadio)

**AMARadio** is a modern, open-source Android application for streaming radio stations from all over the world. It is powered by the community-driven [radio-browser.info](https://www.radio-browser.info/) database.

AMARadio is a fork of the excellent [RadioDroid](https://github.com/ounben/RadioDroid) project, customized for a more focused, accessible, and personal experience.

The codebase has been completely migrated from Java to **100% Kotlin**, ensuring better performance, modern development standards, and full compatibility with the latest Android versions (Android 14/15+).

> [!IMPORTANT]
> **100% Ad-Free & Privacy-Focused**: This app was built with a simple goal: a clean, honest radio experience without annoying ads or tracking. **I originally developed AMARadio for my mother**, who wanted a simple way to listen to the radio without being interrupted by advertisements. This is why the app is and will always remain completely free of ads and bloatware. It's a promise kept for her, and shared with you.

## 📸 Screenshots

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

## ✨ Key Features

- **Advanced Filtering**: Dedicated "Filter" tab to find stations by Country (with flags), Language, and Tags. Your filter settings are saved even after restarting the app.
- **Dynamic UI Scaling**: Built-in accessibility setting to adjust font and button sizes (from Compact to Very Large), ensuring the app is usable for everyone.
- **Massive Database**: Access thousands of radio stations via the community-driven Radio Browser API.
- **Recording**: Record your favorite radio shows directly to your device for offline listening.
- **Chromecast Support**: Stream music seamlessly to your TV or smart speakers.
- **Favorites & History**: Manage your top stations easily. Mark favorites with a single tap on the star icon.
- **Privacy & Safety**: No ads, no tracking, and smart warnings when using metered (mobile) connections to save your data.
- **Modern Design**: Clean Material UI with a specialized black splash screen and full support for Dark and Light modes.
- **Android TV Support**: A UI optimized for the big screen experience.

## 🚀 Getting Started

### Download
You can download the app from:
- [F-Droid](https://f-droid.org/repository/browse/?fdid=net.ounben.AMARadio)
- [Google Play](https://play.google.com/store/apps/details?id=net.ounben.AMARadio)
- [GitHub Releases](https://github.com/segler-alex/AMARadio/releases)

### Building from source
1. Clone the repository:
   ```bash
   git clone https://github.com/segler-alex/AMARadio.git
   ```
2. Open the project in **Android Studio**.
3. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

## 🛠 Technical Details
- **Language**: 100% Kotlin
- **Architecture**: Modern Android Architecture Components (Lifecycle, ViewModel, ViewBinding)
- **Database**: Room for local storage (Favorites, History)
- **Network**: OkHttp3 & Picasso for image loading
- **Target SDK**: 37 (Android 15)

## 🤝 Contributing

Contributions are welcome! Whether it's fixing bugs, adding features, or improving translations.
- Found a bug? [Open an issue](https://github.com/segler-alex/AMARadio/issues).
- Want to contribute code? Fork the repo and submit a pull request.
- Translation help is always appreciated!

## 📄 License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENCE](LICENCE) file for details.

---
<p align="center">
  Made with ❤️ for Radio Lovers.
</p>
