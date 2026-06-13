# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.95] - 2026-06-12
### Added
- **Integrated Station Search in Filter**: You can now search for station names directly within the Advanced Filter tab, combining it with Country, Language, and Tag filters.
- **Improved Tag Support**: Expanded the local tag database to **10,000 entries**, ensuring specialized genres like "Phonk" are easily found.
- **Smart Suggestions**: Added "contains" search logic to all filter dropdowns (Country, Language, Tag). Results are now ranked by relevance, prioritizing exact matches and word starts.
- **UI Drag Handles**: Added visual drag handles to the mini-player and track detail dialogs to indicate they can be swiped.
- **Enhanced Track Details**: The track history info dialog now displays Title, Artist, and Duration in a modern, structured layout.

### Changed
- **Modernized Notifications**: Replaced old-style gray Toasts with sleek, theme-colored Material Snackbars.
- **Optimized Full Screen Player**: Removed unused album art to provide immediate focus on station details and tags.
- **Toolbar Refinement**: 
    - Fixed the title to a static "AMARadio" across all tabs and languages for better branding.
    - Improved title visibility in Dark Mode (now uses brand orange).
    - Maximized search field space by automatically hiding all other icons during search.
    - Compacted icon spacing for a cleaner look on all screen sizes.
- **System Integration**: Fixed layout issues where the app was partially covered by system bars (YouTube-style). The toolbar now remains fixed at the top for a smoother scrolling experience.
- **Navigation Icon**: Updated the stations list icon in the bottom menu to a radio symbol for better clarity.

### Fixed
- **Search Reliability**: Resolved an issue where specific stations (like "Tlemcen") were buried by mid-word matches. Search results are now intelligently ranked.
- **Stability**: Removed the crash-prone Lyrics feature and fixed image loading errors.
- **Build Quality**: Resolved over 150 Android Lint errors and modernized project configurations.

### Security
- **Increased Minimum SDK**: Now targeting Android 8.0 (API 26) as the minimum version to support modern security and performance features.
- **Privacy**: Removed Last.fm integration and related API key settings.

## [0.94] - 2026-06-12
### Added
- **Storage Access Framework (SAF)**: Replaced custom file dialogs with the native Android Storage Access Framework for better security and system integration.

### Changed
- **Image Loading**: Switched from Picasso to Coil for more efficient and modern image loading.
- **String Matching**: Replaced `fuzzywuzzy` with Apache Commons Text for improved search and similarity calculations.
- **Dependency Cleanup**: Removed deprecated `legacy-support-v4` library.
- **Build System**: Updated Gradle to the latest version.

## [0.93] - 2026-06-11
### Added
- **New Grid View**: 
    - Introduced a modern grid-based layout for station lists (Stations, Favorites, and History).
    - Added a Grid/List toggle in the top toolbar using official Material Design icons.
    - Integrated a clickable favorite star directly onto station images in the grid view.
- **Dynamic Grid Scaling**: Grid layout automatically adjusts column counts and item sizes based on the "UI Scaling" preference.

### Changed
- **Toolbar Refinement**: Optimized the top toolbar to feature the cat logo and dynamic title on the left, with the loading progress bar and menu actions on the right.
- **Unified View Management**: Replaced legacy "icons only" favorites style with a global view mode that persists across the entire app.

## [0.92] - 2026-06-11
### Added
- **New Branding "Radio Cat"**:
    - Completely redesigned the app icon featuring a cat image combined with a modern radio symbol.
    - Integrated the cat image as a permanent logo in the top-left of the toolbar.
- **Emoji Flags**: Replaced all 250+ country flag PNG graphics with native Unicode emojis. This reduces APK size and ensures sharp rendering at any zoom level.
- **Material Design Placeholders**: Stations without a logo now feature a sleek Material radio vector icon instead of the old photo symbol.

### Changed
- **Vector Migration**: Deleted all legacy PNG icons (Play, Pause, Stop, Skip, View mode, Filter) and replaced them with official Google Material vector drawables.
- **UI Optimization**: Flag emoji size now automatically adapts to station text size (130% scaling for visual balance).
- **Clean Toolbar**: Repositioned the loading progress bar to accommodate the new cat logo and fixed the double title issue in Settings.

## [0.91] - 2026-06-09
### Changed
- **Migration to KSP**: Migrated the entire project from `kapt` to `Kotlin Symbol Processing (KSP)`. This results in faster build times and better Kotlin compatibility for the Room database.
- **Native UI Components**: Replaced third-party libraries `MaterialPopupMenu` and `SearchPreference` with native AndroidX components for better performance and system integration.
- **Settings Overhaul**: 
    - Flattened the settings structure to a single, easily scrollable page.
    - Added Material icons to every individual setting for better visual guidance.
    - Implemented automatic icon tinting that adapts to the current theme (Light/Dark).
    - Unified the "Compact mode" setting into the "UI Scaling" selection.
- **User Experience**: 
    - Station icons now consistently trigger playback when clicked.
    - Favoriting stations is now exclusively handled by the dedicated star icon to prevent accidental triggers.
    - Standardized all settings icons to a uniform size (24dp).

### Fixed
- **Settings Stability**: Fixed issues where selection menus (Theme, UI Scaling) would not appear on certain devices.
- **UI Scaling**: Improved the reliability of the "recreate" logic when changing UI scale or themes, preventing potential app freezes.
- **Dark Mode Visibility**: Fixed invisible icons in settings when using Dark Mode.

## [0.9] - 2026-06-09
### Changed
- **Official Material Icons**: Completely replaced the third-party `Android-Iconics` library with official Google Material Icons (Vector Drawables). This results in faster app startup and a smaller APK size.
- **UI Standardization**: Replaced all custom icon components with native Android `ImageButton` and `ImageView` components for better system compatibility and performance.
- **Enhanced Animations**: Modernized the "swipe-to-delete" visual feedback using native Material drawables.

## [0.89] - 2026-06-09
### Changed
- **Migration to AndroidX Media3**: Upgraded the streaming engine to the latest `androidx.media3:media3-exoplayer` (v1.5.1). This provides better performance, improved stream stability, and full compatibility with the latest Android standards.
- **Dependency Refresh**: Updated core libraries (AppCompat, Material, Lifecycle, OkHttp) to their latest stable versions.
- **Android SDK Update**: Now targeting Android 15 (API 37) to ensure maximum security and support for new devices.
- **Code Modernization**: Replaced deprecated navigation and back-button logic with modern Android Architecture components (`OnBackPressedDispatcher`).

### Fixed
- **Build System**: Resolved several compilation errors and warnings related to library updates and Kotlin 1.9 compatibility.
- **Stability**: Fixed potential crashes when handling intents and improved null-safety in the ExoPlayer wrapper.

## [0.88] - 2026-06-08
### Added
- **Advanced Filtering**: New dedicated "Filter" tab with search by Country (with flags/names), Language, and Tags.
- **Search Persistence**: Filter settings are now saved and restored upon app restart.
- **Global Filter Access**: Added a new filter icon in the top toolbar for quick access from any screen.
- **Enhanced UI Components**: 
    - Redesigned filter fields with "Clear" buttons and icons.
    - Updated settings icon to a standard "gear" symbol across the app.
    - Improved cursor visibility using the app's accent color in input fields.
- **User Experience**: Station images now act as play buttons, while the star icon exclusively handles favorites to prevent accidental removals.
- **Placeholders**: Added placeholder icons for stations without logos to ensure a stable and consistent layout.

### Fixed
- **Settings**: Fixed the "About AMARadio" link in settings which was previously non-responsive.
- **Layout Integrity**: Resolved overlapping issues in the filter screen when using high UI scaling.
- **CI/CD**: Fixed resource naming inconsistencies to ensure successful builds in GitHub Actions environments.

## [0.87] - 2026-06-08
### Added
- Dynamic UI Scaling: New setting to adjust font and button sizes for better accessibility.
- Black Splash Screen: Modernized startup with a black design and "Ad-Free & Private" promise.
- Accessibility: Scalable play buttons and navigation icons.
- Personal touch: Integrated the story of AMARadio's origin (developed for the developer's mother) to ensure a trustworthy, ad-free promise.

### Changed
- Complete Codebase Migration: 100% of the project migrated from Java to Kotlin.
- Improved Favorites: Redesigned star icon with a larger touch area and distinct visual states.
- Documentation: Major update to GitHub README with store badges and clearer info.
- Standards: Uniform project structure and file naming for better GitHub Workflow compatibility.

### Fixed
- Connectivity: Resolved a loop issue with metered connection warnings on mobile data.
- UI Layout: Fixed small player being partially covered by the system navigation bar.
- Build/Test: Resolved several resource naming conflicts and JUnit 5 test configuration issues.

## [0.86] - 2023-09-28
### Added
- Auto stop support for auto start-play

### Changed
- Enabled android tv again
- Distribute package as AAB on play store from now on
- Sorting of entries from loaded files is now the same as the file

## [0.85] - 2023-09-27
### Fixed
- Building works again
- File dialog on android 13 uses system dialog and works now

### Added
- Translations: norwegian(nb), basque(eu)

### Changed
- Server fallback should work now even when the server return 502

## [0.84] - 2020-12-28
### Added
- Refreshable favorites and history lists
- Mark removed stations red, and broken stations yellow
- Translation updates
- Adaptive launcher icon
- Testing framework
- Stop button to MPD
- Very basic android TV support
- LastFM Api key changeable by user in settings menu

### Fixed
- Recording in android 10
- Correctly display audio players in list of external play
- Play audio warnings as music and not as alarm
- False negatives in hls stream detection

## [0.83] - 2020-04-15
### Changed
- "Remove from favorites" usability
- Track history with icons disabled (#774)

### Fixed
- Added fallback if dns resolve does not return anything
- Fix state updating of record button (#785)
- Show previously picked time when editing alarm's time (#784)
- Start recording after storage permissions are granted (#783)

## [0.82] - 2020-03-07
### Fixed
- Audio focus on pause
- Sudden stop of playback after it beeing resumed after connection loss

### Changed
- Swap station name and track name in full screen player

## [0.81] - 2020-03-03
### Added
- Export history to m3u

### Fixed
- Make sure all.api.radio-browser.info is not used directly
- Play time in fullscreen player
- Some crashes
- Stop notification relaunch after stop
- External player interactions
- Autostart of notification

### Changed
- Library: material 1.2.0-alpha05
- Library: gson 2.8.6
- Library: cast 18.1.0
- Library: lifecycle 2.2.0
- Library: searchpreference 2.0.0

## [0.80] - 2020-02-10
### Added
- Fullscreen player
- Password support for MPD
- Show warning for use of metered connections
- Flag symbols in countries tab
- History of the played tracks
- Stations search now shows results as you type
- Option to resume on wired or bluetooth device reconnection

### Fixed
- Connection issues with android 4 for most people

### Changed
- Library: OKhttp 3.12.8
- Library: Cast 18.0.0
- Use countrycode field from API instead of country field
- Reworked user interface for MPD which now allows explicit management of several servers
- Improved user interface of recordings

### Removed
- Server selection from settings. There is an automatic fallback now.
- Old main server is not used anymore (www.radio-browser.info/webservice)

