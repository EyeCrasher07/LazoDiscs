# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-08-25

### Fixed
- **YouTube playback**: Fixed "Must find action functions from script" / "no actions match" error caused by YouTube player script changes
  - Updated `dev.lavalink.youtube:v2` to working commit `f45bbb7aebfcbc1c553769e04af6cd43afa8b7c3-SNAPSHOT` (same as working Fabric builds)
  - Added `mergeServiceFiles()` in ShadowJar to properly handle relocated Jackson ServiceLoader descriptors
  - Removed problematic `exclude 'META-INF/services/com.fasterxml.jackson.*'` that broke Jackson initialization
- **Jackson version conflicts**: Added proper excludes for Jackson in `dev.arbjerg:lavaplayer` to prevent conflicts with Minecraft's pinned Jackson 2.13.x

### Changed
- Repository restructured: all versions now in single `main` branch under `fabric/<version>/` and `neoforge/<version>/`
- GitHub Actions workflow added for automated matrix builds and releases
- NeoForge dependency versions updated to latest stable for each MC version:
  - 1.21.1: 21.1.10
  - 1.21.2: 21.2.1-beta
  - 1.21.3: 21.3.69
  - 1.21.4: 21.4.121
  - 1.21.5: 21.5.87
  - 1.21.6: 21.6.20-beta
  - 1.21.7: 21.7.25-beta
  - 1.21.8: 21.8.50
  - 1.21.9: 21.9.16-beta
  - 1.21.10: 21.10.50-beta
  - 1.21.11: 21.11.45

### Added
- All 11 Minecraft versions (1.21.1 – 1.21.11) for both Fabric and NeoForge in single repository
- Automated CI/CD via GitHub Actions (matrix build on tag push)
- Single release containing all 22 JARs (11 Fabric + 11 NeoForge)

## [1.0.1] - 2026-07-06

### Added
- Fabric builds for Minecraft 1.21.1–1.21.11
- NeoForge builds for Minecraft 1.21.1–1.21.11
- Improved Spotify and YouTube Music playback reliability
- Fixed custom discs not playing when YouTube rejects one playback client
- Improved jukebox playback stability and disc insert/eject handling

## [1.0.0] - 2026-06-19

### Added
- Initial release
- Burn custom audio links onto vanilla music discs
- Play through Plasmo Voice positional audio
- Spotify track link support via metadata lookup + YouTube Music matching
- YouTube, YouTube Music, SoundCloud, direct audio URLs via LavaPlayer streaming
- Russian/English server-side messages via `display.language`
- `/lazodisc burn`, `/lazodisc erase`, `/lazodisc search` commands
- Jukebox restart cooldown to prevent TPS harm from spam

### Note
Minecraft 1.21.2 not included in 1.0.0 (port not verified at the time)