# LazoDiscs

Custom music discs with Plasmo Voice positional audio for Fabric and NeoForge.

[![GitHub release](https://img.shields.io/github/v/release/EyeCrasher07/LazoDiscs?label=release)](https://github.com/EyeCrasher07/LazoDiscs/releases)
[![Modrinth](https://img.shields.io/modrinth/dt/lazodiscs?label=Modrinth)](https://modrinth.com/mod/lazodiscs)
[![CurseForge](https://img.shields.io/badge/CurseForge-lazodiscs-orange)](https://www.curseforge.com/minecraft/mc-mods/pv-lazodiscs)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

## Features

- Burn music links onto vanilla music discs
- Play custom discs through Plasmo Voice positional audio
- Search for songs from chat and burn tracks from clickable results
- Supports YouTube, YouTube Music, SoundCloud, Spotify track links, and direct playable audio URLs
- Streams playback directly through LavaPlayer
- Keeps one active playback per jukebox and cleans it up automatically
- Supports Sable / Create Aeronautics moving platform positions
- Server owners can edit messages through language files
- No external tools required

## Supported Versions

| Minecraft | Fabric | NeoForge |
|-----------|--------|----------|
| 1.21.1    | ✅     | ✅       |
| 1.21.2    | ✅     | ✅       |
| 1.21.3    | ✅     | ✅       |
| 1.21.4    | ✅     | ✅       |
| 1.21.5    | ✅     | ✅       |
| 1.21.6    | ✅     | ✅       |
| 1.21.7    | ✅     | ✅       |
| 1.21.8    | ✅     | ✅       |
| 1.21.9    | ✅     | ✅       |
| 1.21.10   | ✅     | ✅       |
| 1.21.11   | ✅     | ✅       |

## Requirements

- Minecraft 1.21.1 – 1.21.11
- Fabric Loader + Fabric API (for Fabric) **or** NeoForge (for NeoForge)
- Plasmo Voice (server and client for multiplayer, client only for singleplayer)

## Installation

### Server
1. Install LazoDiscs on the server (matching your loader and Minecraft version)
2. Install Plasmo Voice on the server
3. Players need Plasmo Voice on the client to hear discs
4. Players **do not** need LazoDiscs on the client

### Singleplayer
1. Install LazoDiscs locally
2. Install Plasmo Voice locally
3. Install Fabric API too when using Fabric

## Commands

```
/lazodisc burn <url> [title]
/lazodisc erase
/lazodisc search <song name>
```

- `/lazodisc search` is for song names only
- Use `/lazodisc burn` for Spotify, YouTube, SoundCloud, or direct playable links

### Examples

```
/lazodisc search never gonna give you up
/lazodisc burn https://www.youtube.com/watch?v=dQw4w9WgXcQ
/lazodisc burn https://open.spotify.com/track/... My Song
```

## Spotify

Spotify track links do not provide direct audio streams. LazoDiscs reads the Spotify track metadata when possible, searches for a matching track through YouTube Music, and streams the matched audio.

## Server Config

Config file location:
```
config/lazodiscs/config.toml
```

Language files:
```
config/lazodiscs/lang/<language>.toml
```

Useful settings:
```toml
language = "en_us"

[lavaplayer]
maxConcurrentAudioLoads = 3
maxStreamingTrackLengthSeconds = 0

[security]
requirePermissionForBurnCommand = true
burnPermissionLevel = 2
requirePermissionForEraseCommand = true
erasePermissionLevel = 2
requirePermissionForSearchCommand = true
searchPermissionLevel = 2
requirePermissionForPlay = false
playPermissionLevel = 2
```

The built-in English language file is created as `config/lazodiscs/lang/en_us.toml` on first start. Server owners can edit it or add another file (e.g., `ru_ru.toml`), then set `language = "ru_ru"`.

- `maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length
- `requirePermissionForPlay = false` means players can play already-burned discs by default. Set to `true` to require operator permission for playback too.

## Downloads

- **GitHub Releases**: [All versions](https://github.com/EyeCrasher07/LazoDiscs/releases) (Fabric + NeoForge for all MC versions in one release)
- **Modrinth**: [lazodiscs](https://modrinth.com/mod/lazodiscs)
- **CurseForge**: [pv-lazodiscs](https://www.curseforge.com/minecraft/mc-mods/pv-lazodiscs)

## Building

Each version is a standalone Gradle project:

```bash
# Fabric 1.21.11
cd fabric/1.21.11
./gradlew build

# NeoForge 1.21.11
cd neoforge/1.21.11
./gradlew build
```

Built JARs appear in `build/libs/`. Use the normal JAR (not `-dev`, `-dev-shadow`, `-sources`, or `-thin`).

## Repository Structure

```
LazoDiscs/
├── fabric/
│   ├── 1.21.1/ ... 1.21.11/
│   │   ├── src/
│   │   ├── build.gradle
│   │   ├── settings.gradle
│   │   ├── gradle.properties
│   │   ├── gradlew / gradlew.bat
│   │   └── ...
├── neoforge/
│   ├── 1.21.1/ ... 1.21.11/
│   │   ├── src/
│   │   ├── build.gradle
│   │   ├── settings.gradle
│   │   ├── gradle.properties
│   │   ├── gradlew / gradlew.bat
│   │   └── ...
├── .github/workflows/build.yml
├── README.md
├── LICENSE
├── CHANGELOG.md
└── .gitignore
```

Each version is a completely independent Gradle project with its own `build.gradle`, `settings.gradle`, and wrapper. This avoids version conflicts between Minecraft/loader versions.

## Development

### Prerequisites
- Java 21
- Git

### Local Development
```bash
git clone https://github.com/EyeCrasher07/LazoDiscs.git
cd LazoDiscs

# Work on Fabric 1.21.11
cd fabric/1.21.11
./gradlew runClient

# Work on NeoForge 1.21.11
cd neoforge/1.21.11
./gradlew runClient
```

### Version Matrix
| Component | Fabric | NeoForge |
|-----------|--------|----------|
| Loader | Fabric Loader 0.16+ | NeoForge 21.x |
| Minecraft | 1.21.1 – 1.21.11 | 1.21.1 – 1.21.11 |
| Java | 21 | 21 |
| Plasmo Voice | 2.1.8+ | 2.1.8+ |

## Credits

- Made by **EyeCrasher**
- Based on the original Plasmo Voice Discs addon
- LazoDiscs is not affiliated with or endorsed by the Plasmo Voice developers

## License

GPL-3.0-only — see [LICENSE](LICENSE) for details.