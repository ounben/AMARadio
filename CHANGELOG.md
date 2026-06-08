# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

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

