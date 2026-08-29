# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.28] - 2026-08-29
### Added
- **Custom Stations Feature**: Complete implementation of a new "Custom" tab allowing users to manage their own personal radio streams.
    - **Full CRUD Support**: Add, edit, and delete custom stations with user-defined names and stream URLs.
    - **Local Icon Management**: Support for picking local images from the device gallery. Icons are automatically scaled and stored permanently in a protected internal directory for 100% reliability in Widgets and Android Auto.
    - **Native Drag & Drop Reordering**: Integrated the modern **Jetpack Compose 1.7 Drag & Drop API**. Users can now intuitively sort their custom list using a dedicated "Swap Vert" handle.
    - **Optimistic UI Architecture**: Leverages `SnapshotStateList` and local memory buffering to provide 0ms latency during reordering, with background SQL persistence to prevent UI stuttering.
    - **Smart Metadata Integration**: Custom stations are fully matched against the global catalog. If a stream URL exists in the database, the app automatically inherits official metadata, tags, and languages.
    - **Unified Grid & List Design**: Full support for both Grid and List view modes, following the app's established "Flat Design" principles.
### Changed
- **Media3 Performance Optimization**: Successfully migrated the Media3 Artwork loading system to the **Coil** image loading framework. 
    - This eliminates redundant "naked" HTTP downloads by the system and utilizes the app's existing high-performance disk and memory caches.
    - Improved memory management through automated downsampling, preventing OOM issues on lower-end devices during station transitions.

## [1.26] - 2026-08-23
### Added
- **Extended Metadata Support**: Full support for **Ogg Vorbis** and **Opus** stream metadata.
    - Dynamically extracts Artist and Title information from Vorbis Comments and processed Media3 metadata fields.
    - Seamlessly integrated into the existing metadata pipeline, ensuring high compatibility with Android Auto and system notifications.
    - Centralized parsing logic in a new `MetadataHandler` for better maintainability and robustness across different player types (Local and Cast).
- **Chained Stream Support**: Enhanced handling of Ogg "chained" streams, allowing for instant track title updates during continuous playback without requiring a stream restart.

## [1.25] - 2026-08-19
### Added
- **Google Cast (Chromecast) Support**: Full integration with the modern **Media3 Cast** framework. 
    - Stream your favorite stations directly to Chromecast-enabled TVs and speakers.
    - Features a native **MediaRouteButton** in the top toolbar for seamless device discovery and connection.
    - Automatic player hand-off between local device and remote Cast receiver.
- **Smart Metadata Cleaning**: Implemented a specialized extraction logic for technical stream metadata (specifically for **iHeartRadio** and **Kiss FM**). Technical tags like `text="..."` are now automatically cleaned to show only the pure Artist and Song Title.
- **Startup Resilience (Gate-System)**: Introduced an atomic loading gate (`isLoadedFromDb`) in the Filter Tabs logic. This prevents a race condition where empty initial states could accidentally overwrite existing user data in the database during slow system startups.

### Changed
- **Optimized Pager Initialization**: Refined the tab pager state to be more robust during the transition from loading to displaying content.

## [1.24] - 2026-08-14
### Added
- **Media Control Restoration**: Re-enabled and stabilized "Next" and "Previous" skip buttons in the Android System Notification and Lockscreen player. 
- **Intelligent Queue Fallback**: Implemented an automatic queue discovery system. Stations started via search or widgets now automatically use the History or Favorites list as a backup queue for skip operations.
- **Modernized Core Infrastructure**: Upgraded to **Gradle 9.7.0** and updated key dependencies including **Media3 (1.11.0)** and **Compose BOM (2026.08.00)** for improved performance and security.

### Fixed
- **MediaSession Command Routing**: Corrected a mapping issue where skip commands were occasionally rejected by the system due to strict Media3 live-stream policies.

## [1.23] - 2026-08-11
### Added
- **Anti-Ghost-Task Architecture**: Migrated Widget interactions to direct service starts. This permanently resolves the "black screen" ghost task issue in Android 15 Recents (especially on Oppo/Realme devices).
- **Hardenend Widget Sync**: Implemented a sequential update model (Mutex) and prioritized state-tracking to prevent sporadic "hanging" of track info or play/pause icons.
- **Optimized Android Auto Browser**: 
    - Restructured hierarchy to respect the 4-folder limit (Lokal, Filter, Favorites, History).
    - Implemented "Pagination-First" metadata building, ensuring high-quality icons load instantly even in large local lists (300+ stations).
- **Synchronized Tab State**: Switching tabs in the widget now correctly preserves and refreshes the current player status without flickering.

### Fixed
- **Widget Stop Reliability**: Resolved an issue where track data remained visible after stopping playback from the widget.
- **Localization**: Standardized "No Results" messages in widgets using localized system strings.

## [1.22] - 2026-08-05
### Fixed
- **Trackhistory Stability**: Resolved a potential `NullPointerException` and crash in the history list by implementing safe paging-item peeking and unique fallback keys.
- **Crash Prevention (Widget Background Start)**: Resolved a `BackgroundServiceStartNotAllowedException` that occurred when starting playback from the home screen widget after the app had been in the background for a long time. The app now uses modern foreground service initialization for all widget actions.
- **Layout Stability (120Hz/Android 16)**: Fixed a critical `IllegalArgumentException` in the subcomposition layer. Optimized lazy lists and pagers with unique keys and explicit content types to ensure smooth performance on high-refresh-rate devices.
- **API Traffic Throttling**: Implemented a 5-second "cooldown" for station click reporting. This prevents duplicate network calls and protects community API resources while maintaining accurate station rankings.

## [1.21] - 2026-08-04
### Fixed
- **ANR Prevention (Network I/O)**: Resolved a critical Main-thread block in `PlayStationTask` by offloading network response processing and stream reading to the `Dispatchers.IO` background thread.
- **Widget Performance Optimization**: Hardened the `WidgetUpdateHelper` with a smart state-filtering mechanism. This prevents redundant "Update Storms" and potential system drosseling while maintaining 100% data consistency.

## [1.20] - 2026-07-31
### Fixed
- **Crash Prevention (ActivityNotFoundException)**: Implemented robust safety nets for all external intents. The app no longer crashes if a system component (like a file manager or web browser) is missing or disabled; instead, it provides clear user feedback.
- **MediaSession Stability**: Resolved a critical `IllegalStateException` ("Session ID must be unique") by implementing dynamic, timestamp-based session IDs. This prevents race conditions during rapid service restarts on specific hardware (e.g., Xiaomi/Redmi).

## [1.19] - 2026-07-31
### Added
- **Jetpack Glance Home Screen Widgets**: Introduced two modern, high-performance widgets for the Android Home Screen.
    - **AMARadio Compact (4x1)**: Sleek player controls with real-time station info.
    - **AMARadio Player (4x3)**: Full-featured widget with player controls and a scrollable list of Favorites/History.
- **Push-Model Sync Architecture**: Implemented a reactive state-push mechanism that updates widget lists in less than 200ms when favorites are added or playback changes.
- **Live Track Metadata**: Widgets now display real-time song information (Artist - Title) during playback, with an automatic fallback to genre tags when idle.
- **Themed Professional Previews**: Replaced generic app icons in the widget picker with realistic, adaptive XML-based previews that respect System Light/Dark modes.
- **Anti-Trampoline Interaction**: Migrated all widget clicks to an explicit broadcast system, eliminating UI lags and "Invisible Activity" hangs found in standard Glance implementations.

### Changed
- **SQL-First Initialization**: Completely removed the legacy `fallback_stations.json` system. The app now performs an intelligent local SQL query on the first start to provide the most popular stations for the user's specific region.
- **Optimized UI Responsiveness**: Reduced internal synchronization delays to 150ms for a near-instant feedback loop between the app and the system launcher.

## [1.18] - 2026-07-30
### Added
- **Dual-Database Architecture**: Implemented a strict separation between the global station catalog (`radio_browser_database.db`) and personal user data (`user_data.db`). This ensures user settings are never lost during global database updates.
- **SQL Migration**: Migrated Favorites, Station History, and Filter Tabs from legacy JSON storage to high-performance SQL tables.
- **Improved Android Auto Sync**: Lists in the vehicle now use direct SQL queries and Room flows, ensuring 100% consistency with the smartphone app.
- **Real-time Update Signals**: Implemented reactive triggers to immediately refresh Android Auto displays when history or favorites change on the smartphone.
- **Optimized Car History**: Added a smart 10-station limit for the history folder in Android Auto to enhance driver safety and focus.

### Fixed
- **Filter Tab Stability**: Resolved a critical issue where editing filters caused UI flickering and temporary loss of search results by implementing atomic SQL transactions and smart state merging.
- **History Sync Lag**: Eliminated the delay between starting a station on the phone and seeing it in the car's history list.

## [1.17] - 2026-07-30
### Added
- **Android Auto Stability (Media-Anchor)**: Implemented a robust "Media-Anchor" system to prevent session disconnects during station transitions. The app now strictly reports a loading state instead of falling into IDLE (0), ensuring the player window remains active in the car.
- **HLS/TS Stream Support**: Re-enabled and optimized Transport Stream (.ts) support for HLS-based radio stations (e.g., Radio Italia).
- **Nested Browser Hierarchy**: Reorganized the Android Auto browser for better clarity. Filters and custom tabs are now grouped in a dedicated "Discover" folder, while Favorites and History remain instantly accessible.
- **Compact List UI**: Refined the look of folders in the vehicle display to match the sleek station list style (List-mode instead of Grid-mode).

### Changed
- **Optimized Car Metadata**: Reordered track information for better readability behind the wheel. The **Station Name** is now the primary focus (Line 1), while the current song title follows in the second line.
- **Direct History Retrieval**: Simplified the synchronization between the smartphone and the vehicle. Android Auto now queries the station history directly from the local SQL storage whenever a folder is opened, ensuring 100% consistency.
- **Media Type Normalization**: Standardized the entire metadata pipeline to use the `MUSIC` (1) media type, resolving compatibility issues on Android 15, 16, and 17 where the specialized `RADIO_STATION` (21) type was occasionally rejected.

### Fixed
- **"Gray Player" Elimination**: Resolved the issue where the car display would momentarily go empty during station switches by implementing a persistent metadata "vaccination" logic.
- **Hardware Decoder Resilience**: Increased the grace period during codec handovers (MP3 <-> AAC) to 250ms, preventing `C2_CORRUPTED` errors on modern hardware chipsets.

## [1.16] - 2026-07-29
### Added
- **Modern Build Infrastructure**: Fully migrated to **Android Gradle Plugin 9.0** and **Target SDK 37 (Android 17)** standards.
- **Kotlin Integration**: Transitioned to built-in Kotlin support, removing legacy plugins for better build performance.
- **Edge-to-Edge System Design**: Refined the UI to strictly follow modern Android standards with transparent system bars and automatic `WindowInsets` handling.

## [1.15] - 2026-07-25
### Added
- **SQL Metadata Architecture**: Migrated Genre-Tags and Languages to a high-performance local SQL cache (`TagCache` and `LanguageCache`). This allows for instant, offline filter suggestions and supports massive datasets (e.g., 11,000+ tags) without performance degradation.
- **Community Support (Click Counting)**: Integrated an asynchronous "Fire & Forget" reporting mechanism for the official radio-browser.info API. Playing a station now supports its global ranking via the official click count system.
- **Expanded Results**: Increased the maximum number of stations displayed in searches and country lists from 100 to **300**, providing a broader selection for users.

### Fixed
- **Database Schema Integrity**: Resolved a `IllegalStateException` related to SQL index ordering (`DESC` vs `ASC`) in the pre-packaged database.

## [1.14] - 2026-07-24
### Added
- **Icon Baking Engine**: Implemented a proactive background processing system for station logos. The app now automatically "bakes" (downloads and resizes) icons to a local cache, ensuring 100% icon coverage and instant delivery to System Notifications and Bluetooth units.
- **Enhanced Search Weighted Scoring**: Significant overhaul of the search algorithm in Filters. "Starts-with" matches and word boundaries are now heavily prioritized (e.g., "Sweden" or "Switzerland" appear first when searching "sch"), drastically improving relevance for countries and tags.
- **Adaptive UI Scrolling**: Searchable dialogs (Country, Language, Tag) now automatically scroll back to the top when the search query changes, ensuring the most relevant results are always visible.

### Changed
- **Modern Build Infrastructure**: Fully migrated to **Android Gradle Plugin 9.0** standards.
- **Kotlin Integration**: Transitioned to built-in Kotlin support, removing legacy plugins for better build performance and future compatibility.
- **Edge-to-Edge System Design**: Refined the UI to strictly follow modern Android 15 standards with transparent system bars and automatic `WindowInsets` handling.
- **Internal Cleanup**: Removed deprecated icon usage and replaced them with official `AutoMirrored` Material versions.

### Fixed
- **Audio Engine Stabilization**: Hardened the ExoPlayer wrapper with an explicit hardware-stop and thread-grace-period mechanism. This resolves "pipelineFull" and MediaCodec overflow errors during rapid station switching.
- **Android 14+ Compatibility**: Fixed a potential crash (`ForegroundServiceStartNotAllowedException`) by implementing a safer foreground state transition for the Player Service.
- **UI Stability**: Resolved a fatal `IndexOutOfBoundsException` when deleting custom filter tabs by implementing safe index coercion in the TabRow components.
- **Database Resilience**: Updated Room configuration to use the modern parameterized `fallbackToDestructiveMigration` API.


## [1.13] - 2026-07-19
### Added
- **Room Database Optimization**: `StationUuid` is now the primary key for reliable updates (`REPLACE` strategy). Redundant `StationID` removed.
- **Enhanced Synchronization Logic**: Switched to the API endpoint `json/stations/lastchange`.
- **Intelligent Update Triggers**: Automatic database sync 5 minutes after app start if the last sync is older than 24 hours.
- **Statistics Expansion**: New "Local Database" tab showing the count of functional stations, last sync timestamp, and a list of the 5 most recent changes (interactive).
- **Strict Data Filtering**: All local lists (search, filter, countries) now only display stations with a positive availability status (`LastCheckOK = 1`).

### Changed
- **UI Cleanup**: Removed Proxy settings and "Show broken stations" option from the user interface.
- **Layout Corrections**: Fixed vertical centering of elements in the settings menu.
- **Sync Parameters**: Background synchronization increased to 1000 stations per run.

## [1.12] - 2026-07-15
### Changed
- **Automotive OS Compatibility**: Removed explicit Android Automotive OS (Standalone) compatibility descriptors from the manifest and resource files. This ensures the app is primarily targeted as a high-performance Android Auto (Smartphone-connected) media application, avoiding redundant Standalone-Automotive review processes while maintaining full vehicle head-unit support via Android Auto.

## [1.11] - 2026-07-15
### Added
- **Dynamic Station Placeholders**:
    - Implemented a sophisticated fallback system for stations missing official logos.
    - **Intelligent Text Extraction**: Automatically detects and displays relevant identifiers (e.g., "2" from "WDR 2", "RA" from "Rock Antenne").
    - **Deterministic Coloring**: Each station is assigned one of seven high-contrast Material Design colors based on its unique UUID, ensuring a consistent and professional look.
    - **Universal Integration**: These dynamic placeholders are now visible in the Smartphone UI, System Notifications, and Android Auto head units.
- **Proactive Playback Warnings**:
    - **Connection Guard**: Added an instant alert if a user tries to start playback without an active internet connection.
    - **Volume Monitor**: Warns the user if playback is started while the media volume is set to zero, preventing "silent play" confusion.

### Changed
- **Enhanced Visual Consistency**: Replaced generic radio icons with the new dynamic placeholders throughout the app's history and station lists.
- **UI Performance**: Optimized the bitmap generation logic for placeholders to ensure perfectly smooth scrolling in large lists.

## [1.10] - 2026-07-15
### Added
- **Local-First Database Architecture**:
    - Integrated a high-performance **Room Database** for all station management.
    - **Instant Search**: All searches (Manual, Filter, Local) now query the local database first, providing near-instant results even offline.
    - **Pre-populated Database**: The app now ships with a 70MB high-quality snapshot of the radio-browser database, ensuring thousands of stations are available immediately after installation.
    - **Automated Background Sync**: A new `SyncWorker` periodically updates the local database with the latest 1000 changes from the global API using an intelligent server cascade.
- **Enhanced Android Auto Experience**:
    - Simplified and stabilized folder structure mirroring the smartphone app.
    - **Station Tabs**: "Local" and custom filter tabs are now directly accessible as folders.
    - **Icon Integration**: Added high-contrast Material icons for Stations, Favorites, and History in the car display.
    - **Offline Browsing**: Android Auto menus now load instantly from the local Room database, eliminating loading delays while driving.
- **Resilient Background Sync**:
    - Implemented an intelligent server cascade (Official -> Rotation -> Mirror) for background updates.
    - Optimized sync triggers: Runs daily on any connection (WLAN/Mobile) with a 30-minute startup delay to ensure app stability.

### Changed
- **Performance Optimization**: Completely removed the need for real-time JSON parsing of large station lists during navigation.
- **Play Stability**: Hardened the playback logic in Android Auto by decoupling browser navigation from the internal PlayStationTask.

## [0.99.9] - 2026-07-04
### Added
- **Android Auto Integration**:
    - Full support for Android Auto and Automotive OS.
    - Specialized Media Browser: Favorited stations and history are now directly accessible from the vehicle's head unit.
    - Filter Tabs as Folders: Your personal genre and country filters from the app now appear as browsable folders in the car.
    - Optimized "Now Playing" UI: Large, readable titles and controls designed for safe operation while driving.
    - Intelligent Metadata Splitting: Automatically detects and splits ICY metadata into separate Title and Artist fields for a professional look in the car display.
- **GitHub CI Workflow**:
    - Added an optimized automation pipeline for GitHub Actions.
    - Features rapid builds using Java 21 and Gradle caching, and automated APK artifact generation.

### Fixed
- **Automotive Player Stability**: Resolved issues where playback controls (Play/Pause) and track information were occasionally missing in certain vehicle systems by implementing proactive player preparation and extended Media3 command sets.
- **Metadata Synchronization**: Improved the reliability of artwork and title updates across the MediaSession and system notifications.

## [0.99.8] - 2026-07-04
### Fixed
- **App Bundle Language Support**: Configured the build system to include all language resources in the App Bundle (AAB). This fixes the issue where in-app language switching wouldn't work when installed via the Google Play Store due to language-based APK splitting.
- **Language Selection Stability**: Fixed a sync issue between the settings menu and the internal locale manager by using a consistent SharedPreferences context.
- **Activity Compatibility**: Modernized `ActivityMain` by migrating to `AppCompatActivity` for better support of per-app language preferences.

## [0.99.7] - 2026-07-04
### Added
- **In-App Review Integration**:
    - Integrated Google Play In-App Review API for seamless user feedback.
    - Added a non-intrusive "Rate AMARadio" option at the top of the settings menu.
    - Implemented intelligent review logic: Automated prompts appear only after 100 significant actions (playing stations or adding favorites) with a 7-day cooldown.
    - Comprehensive localization for review prompts in over 30 languages.
    - Integrated a robust fallback mechanism that redirects to the Play Store if the API is unavailable.

## [0.99.6] - 2026-06-30
### Added
- **Global Accessibility (TalkBack)**:
    - Optimized the entire UI for blind and visually impaired users.
    - Implemented semantic grouping for radio stations, providing clear, narrated summaries instead of fragmented clicks.
    - Added "speaking" status icons for favorites and search states.
    - Localized accessibility descriptions across all 74 supported languages.
- **New Languages (African Region)**:
    - Added full support for **Afrikaans (af)**, **Amharic (am)**, **Swahili (sw)**, and **isiZulu (zu)**.

### Changed
- **Stability**: Finalized language management and improved locale switching reliability on older Android devices.
- **Resource Optimization**: Minor cleanup of string resources and localized assets.
- **Automated Build Naming**: Configured Gradle to automatically name App Bundles and APKs using the "AMARadio_Version" format for better release management.

## [0.99.5] - 2026-06-30
### Added
- **Language Selection**:
    - Introduced a new "Language" setting in the appearance menu.
    - Users can now manually override the system language and choose from over 25 supported languages.
    - Added a persistent locale management system that ensures the selected language is applied across app restarts.
- **Enhanced Localization**:
    - Updated native language names in the selection list for better accessibility.
    - Integrated proper `AppCompatDelegate` per-app language support.

## [0.99.4] - 2026-06-30
### Added
- **Language Support**:
    - Added full support for **Japanese (ja)** and **Hindi (hi)** with complete translations of all app features.
    - Completed and updated the **Arabic (ar)** translation.
- **Visual Branding**:
    - Updated the Splash Screen with the brand's Amber color for the "AMARadio" title.
    - Added the version number to the Splash Screen for easier identification.
    - Modernized the logo presentation with rounded corners in both the Splash Screen and the main Toolbar.
- **Improved Navigation**:
    - Added a dedicated Back button to the Statistics and About screens within the settings.
    - Optimized the bottom navigation behavior: clicking "Settings" while in a sub-screen now returns you directly to the main settings menu.
- **Play Integrity Migration**: Replaced the deprecated SafetyNet Attestation API with the modern **Play Integrity API**. This ensures long-term support for device and app integrity checks.
- **System Privacy Compliance**: Added formal `attributionTag` declaration in the manifest and implemented `AttributionContext` for all media operations. This resolves system-level "attributionTag not declared" errors on modern Android versions (API 31+).

### Fixed
- **Player Stability**: Resolved a race condition where the player would occasionally stop immediately after starting a new station. The "Idle" state from a stopping station no longer interferes with the "PrePlaying" state of a new one.
- **Audio Focus Management**: Hardened the audio focus logic to ensure AMARadio reliably stops when other media apps (like YouTube or TikTok) start playing. 
- **Automatic Media Handling**: Enabled modern Media3 automatic focus management, providing a more consistent behavior when competing with system sounds or other media players.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

## [0.99.3] - 2026-06-29
### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Stream Compatibility**: Implemented a robust playlist resolver that automatically handles `.m3u` and `.pls` files. This fixes playback for many stations (e.g., SRF Musikwelle) that were previously failing with container errors.
- **Clean Audio Engine**: Rewrote the ICY metadata filter to be byte-accurate. By stripping metadata before it reaches the player and hiding the `icy-metaint` header from ExoPlayer, we've eliminated the "start-stop" stuttering and "UnrecognizedInputFormatException" errors.
- **Server Connectivity**: 
    - Standardized the User-Agent to "AMARadio" to ensure compatibility with restrictive radio servers.
    - Added a `HEAD`-to-GET interceptor to support servers that fail on header-only probes.
    - Integrated dynamic timeouts (Connect/Read) directly into the stream initialization from app settings.

### Changed
- **Metadata Resilience**: Switched to a regex-based ICY parser, making title and artist detection more reliable across various international encoding standards.

## [0.99.2] - 2026-06-25
### Added
- **Instant UI Experience**: Migrated to an Activity-scoped ViewModel architecture and a persistent `hide/show` fragment navigation. This eliminates flickering and makes switching between Stations, Favorites, and History instantaneous.
- **Background Pre-Rendering**: Implemented a staged background initialization that "warms up" all app sections (including the full player drawer) shortly after startup, ensuring they are ready before the first click.
- **Offline Filter Metadata**: Integrated a comprehensive local database of 11,000 genre tags, countries, and languages. The Advanced Filter now provides instant, ranked suggestions (by station count) even without an internet connection.

### Changed
- **Smart Caching**: Refactored the API cache to use MD5-hashed, server-independent storage. Repeat visits to countries or search results are now loaded instantly from disk regardless of server rotation.
- **Performance Optimization**: 
    - Optimized Jetpack Compose list rendering using stable UUID keys and background data processing, completely eliminating "skipped frames" during scrolling.
    - Decoupled playback start from background storage tasks (History/Favorites) for a faster "Play" response.
- **Data Resilience**: Implemented a "lightweight copy" mechanism for inter-process communication to prevent `TransactionTooLargeException` crashes when handling stations with extensive metadata or Chinese characters.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Favorites Management**: Fully restored and modernized the Favorites Import/Export (M3U) functionality with support for large lists and UTF-8 encoding.
- **Stability**: 
    - Resolved critical 60-second UI freezes (ANR) caused by redundant layout transitions in the navigation bar.
    - Fixed a fatal build error (Lint) related to RecyclerView size constraints in the full-screen player.
    - Unified the "Grid/List" view toggle behavior to be consistent and reactive across the entire app.
- **Clean Code**: Removed obsolete classes (e.g., `StationsFilter`) and dozens of unused legacy functions, reducing technical debt.

## [0.99.1] - 2026-06-23
### Added
- **Modern Build Configuration**: Upgraded `compileSdkVersion` and `targetSdkVersion` to **37** (Android 15) and transitioned to **Java 21** and **JVM 21** for better performance and modern API support.
- **Enhanced Release Security**: Enabled `minifyEnabled` and `shrinkResources` in the release build to protect the code and reduce APK size.
- **TV Compatibility**: Improved app visibility on Ethernet-only Android TV devices by explicitly declaring Wi-Fi as a non-mandatory feature.

### Changed
- **Code Modernization**:
    - Replaced the deprecated `kotlinOptions` with the modern `kotlin.compilerOptions` DSL in `build.gradle`.
    - Fully migrated from the deprecated `adapterPosition` to `bindingAdapterPosition` for more reliable RecyclerView item interactions.
    - Standardized internal function naming from PascalCase to standard Kotlin **camelCase** (e.g., `RefreshListGui` -> `refreshListGui`).
- **Optimization**:
    - Replaced inefficient `notifyDataSetChanged()` calls with `DiffUtil` in `ItemAdapterStation` and `ItemAdapterCategory`, resulting in smoother animations and better list performance.
    - Optimized connectivity checks by removing legacy `BroadcastReceiver` logic in favor of the modern `NetworkCallback` API.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Stability & Cleanliness**:
    - Resolved over 50 lint warnings, including redundant safe calls, unnecessary non-null assertions, and qualified names.
    - Improved resource management by ensuring all I/O operations in `StreamProxy` are correctly wrapped in `withContext(Dispatchers.IO)` and resources are properly closed.
    - Fixed a long-standing issue where the "Pause on headset disconnect" feature would fail on certain Android versions due to missing receiver registration.
    - Removed dozens of unused functions, imports, and variables across the entire project.
- **Media Session**: Hardened `PlayerService` with proper Media3 permissions and removed the deprecated `MediaButtonReceiver` in favor of internal Media3 handling.
- **UI Refinement**: Replaced manual "..." strings with the proper ellipsis character (&#8230;) in all translations.

## [0.99] - 2026-06-22
### Added
- **Resilient Network Cascade**: Implemented a fail-over mechanism for API requests. If the primary `radio-browser.info` servers are unreachable (503, timeout), the app automatically switches to the `radiobrowser.ounben.com` mirror.
- **Asynchronous Search**: Migrated the station filtering and search engine to Kotlin Coroutines. Searches now run on `Dispatchers.IO`, keeping the UI perfectly fluid during heavy network activity.
- **Audio Thread Prioritization**: Introduced a dedicated high-priority "AudioThread" (`THREAD_PRIORITY_URGENT_AUDIO`). Both ExoPlayer and the internal player logic now run on this thread to eliminate stuttering caused by background system processes.

### Changed
- **Sturdier Buffer Management**: Significantly increased player buffers (50s min, 100s max) and enabled `setPrioritizeTimeOverSizeThresholds`. This provides much better stability on devices with aggressive CPU throttling (Xiaomi/Oplus).
- **Persistent Media Resources**: The player now keeps hardware resources (MediaCodec, AudioTrack) active during temporary buffering or idle states, preventing the "clicks" and silence gaps caused by hardware re-initialization.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Stability Fixes**:
  - Resolved a `NullPointerException` in `ViewPager.onSaveInstanceState` related to IME (keyboard) layout changes.
  - Fixed a critical `IllegalStateException` where the player was accessed from the wrong thread during notification updates.
- **MediaRouter Debouncing**: Implemented filtering for redundant system "pings" to the MediaRouter, preventing unnecessary audio stack resets.
- **Navigation Resilience**: Switched critical fragment transactions to `commitAllowingStateLoss()` to prevent crashes during rapid navigation or state-saving cycles.
- **System Service Hardening**: Implemented safe casting (`as?`) and null-safety for all system services (`UiModeManager`, `PowerManager`, `AudioManager`, `TelephonyManager`, `ConnectivityManager`). This prevents `ClassCastException` on devices with customized Android frameworks.
- **Application Context Robustness**: Fixed a critical crash in `AMARadioApp.onCreate` by ensuring thread-safe dispatcher initialization and adding defensive checks for application context casting.
- **Memory Leak Prevention**: Refactored `PlayerServiceUtil` to use `applicationContext` instead of potentially leaking `Activity` contexts. Implemented defensive null-checks and safe unbinding logic to ensure long-term process stability.
- **Reflection Cleanup**: Completely removed all direct usage of Java Reflection (`Method.invoke`, `Field.get`) to improve stability on devices with aggressive manufacturer-specific optimizations (Oplus/Realme) and to ensure compatibility with modern Android runtime restrictions.
- **UI & Adapter Stability**: Hardened `ItemAdapterStatistics` and `FragmentTabs` against null layout inflators and missing hardware features (e.g., tablets without SIM slots).

### Removed
- **Alarm Clock Feature**: Completely removed the integrated alarm clock functionality. This decision was made to ensure 100% stability across all Android devices, as modern background execution limits and manufacturer-specific "app freezers" (e.g., Oplus/Xiaomi) often compromised the reliability of background alarms. AMARadio now focuses exclusively on high-performance radio streaming.

## [0.98] - 2026-06-15
### Changed
- **Target SDK 36 (Android 16)**: Finalized the migration to Android 16 to meet the latest Play Store requirements.
- **Privacy Audit**: Completed a full audit of app permissions and dependencies. Verified that the app is 100% Java-free and utilizes only essential permissions.
- **Notification Refinement**: Set the notification category to `CATEGORY_TRANSPORT` for improved system-level media handling.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Foreground Service Compliance**: Optimized the service start logic to strictly comply with Android 15+ background start restrictions, preventing potential crashes when starting playback.
- **Alarm Logic**: Cleaned up legacy `AlarmManager` checks to ensure seamless operation on API 33-36 without unnecessary user prompts.

## [0.97] - 2026-06-15
### Added
- **AndroidX Media3 Migration**: Completed the transition of the entire media playback and session architecture to the modern Media3 framework.
- **Library Browser**: Re-implemented the Media Library Service to support improved media browsing and Android Auto integration.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Type Safety**: Resolved several generic and type-related compilation errors in the service layer.
- **Stability**: Fixed a critical crash during session initialization by ensuring the player is created on the main thread.

## [0.96] - 2026-06-13
### Added
- **New Branding "El Gato"**: Completely updated the app logo to the new "El Gato" design across the Splash screen, Toolbar, and Shortcuts.
- **Improved UI Scaling in Settings**: All icons in the settings menu now strictly follow a 24dp base size and scale dynamically with the global "UI Scaling" preference.
- **Visual Feedback**: Active stations in Grid View are now highlighted with a sleek **4dp orange border** for better visibility.
- **Toolbar Styling**: Refined the main toolbar title to 18dp Bold with optimized spacing (8dp margin) to prevent cutoff at high UI scales.

### Changed
- **Feature Cleanup**: Completely **removed the "Recording" feature** (including AIDL and UI assets) to streamline the app and improve stability.
- **Modernized Full Screen Player**: Simplified the layout by removing unnecessary bitrate/network info, focusing on centered controls and station details.
- **System UI**: Implemented `enableEdgeToEdge` with a forced white status bar style for consistent high contrast across all themes.
- **Unified Station Icons**: Standardized the radio placeholder icon to a consistent light gray and unified the loading logic to prevent flickering or incorrect images when scrolling.
- **Service Management**: Optimized the background service to promote itself to the foreground only when active, improving battery life and system compliance.

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- **Stability**: 
    - Resolved a critical crash (`TransactionTooLargeException`) when minimizing the app with large station lists.
    - Fixed a **StackOverflow crash** in the Alarm tab caused by decoupled listener logic.
    - Fixed the "Black Screen" issue when expanding the full-screen player from the mini-player.
    - Corrected foreground service handling to prevent background crashes on Android 12, 13, and 14.
- **Localization**: 
    - Implemented **localized country names** (e.g., "Deutschland" instead of "Germany") across the entire app.
    - Fixed a critical crash in the Portuguese (Brazil) translation caused by a formatting error.
    - Fixed a typo in the Splash Screen and "About" text where "100%" was incorrectly displayed as "100%%" across multiple languages.
- **Audio Focus**: Fixed a conflict where the internal player and the service would fight over audio control, causing sudden stops.
- **Interactivity**: Made the mini-player station icon clickable to expand the full-screen player.
- **Search Weighted Scoring**: Improved local search results using a new weighted scoring system (Exact matches > Prefixes > Word starts).

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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
### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- Recording in android 10
- Correctly display audio players in list of external play
- Play audio warnings as music and not as alarm
- False negatives in hls stream detection

## [0.83] - 2020-04-15
### Changed
- "Remove from favorites" usability
- Track history with icons disabled (#774)

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- Added fallback if dns resolve does not return anything
- Fix state updating of record button (#785)
- Show previously picked time when editing alarm's time (#784)
- Start recording after storage permissions are granted (#783)

## [0.82] - 2020-03-07
### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

### Fixed
- Audio focus on pause
- Sudden stop of playback after it beeing resumed after connection loss

### Changed
- Swap station name and track name in full screen player

## [0.81] - 2020-03-03
### Added
- Export history to m3u

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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

### Changed
- **Translation Maintenance**:
    - Performed a comprehensive cleanup of localized strings across all languages, removing obsolete entries for retired features (MPD, Alarm Clock, Recording).
    - Fully modernized the **Basque (eu)** translation, bringing it up to date with all recent UI features and settings.
    - Standardized resource naming across the project, ensuring all languages use `strings.xml` consistency.
    - Replaced manual "..." with proper ellipsis characters in several language files for better typography.

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
