# LazoDiscs

LazoDiscs lets players burn music links onto vanilla music discs and play them from jukeboxes through Plasmo Voice positional audio.

[![GitHub release](https://img.shields.io/github/v/release/EyeCrasher07/LazoDiscs?label=release)](https://github.com/EyeCrasher07/LazoDiscs/releases)
[![Modrinth](https://img.shields.io/modrinth/dt/lazodiscs?label=modrinth)](https://modrinth.com/mod/lazodiscs)
[![CurseForge](https://img.shields.io/curseforge/dt/1584136?label=curseforge)](https://www.curseforge.com/minecraft/mc-mods/pv-lazodiscs)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

## Downloads

- [GitHub Releases](https://github.com/EyeCrasher07/LazoDiscs/releases)
- [Modrinth](https://modrinth.com/mod/lazodiscs)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/pv-lazodiscs)

## Requirements

- Minecraft `1.21.1` - `1.21.11`
- Fabric or NeoForge
- Plasmo Voice
- Fabric API for Fabric builds

## Features

- Burn music links onto vanilla music discs.
- Play custom discs through Plasmo Voice positional audio.
- Search for songs from chat and burn tracks from clickable results.
- Supports YouTube, YouTube Music, SoundCloud, Spotify track links, and direct playable audio URLs.
- Streams playback directly through LavaPlayer.
- Keeps one active playback per jukebox and cleans it up automatically.
- Supports Sable / Create Aeronautics moving platform positions.
- Server owners can edit messages through language files.
- No external tools are required.

## Commands

```text
/lazodisc burn <url> [title]
/lazodisc erase
/lazodisc search <song name>
```

`/lazodisc search` is for song names only. Use `/lazodisc burn` for Spotify, YouTube, SoundCloud, or direct playable links.

Examples:

```text
/lazodisc search never gonna give you up
/lazodisc burn https://www.youtube.com/watch?v=dQw4w9WgXcQ
/lazodisc burn https://open.spotify.com/track/... My Song
```

## Spotify

Spotify track links do not provide direct audio streams. LazoDiscs reads the Spotify track metadata when possible, searches for a matching track through YouTube Music, and streams the matched audio.

## Server Config

Config file:

```text
config/lazodiscs/config.toml
```

Language files:

```text
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

The built-in English language file is created as `config/lazodiscs/lang/en_us.toml` on first start. Server owners can edit it or add another file, for example `ru_ru.toml`, then set `language = "ru_ru"`.

`maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length.

`requirePermissionForPlay = false` means players can play already-burned discs by default. Set it to `true` to require operator permission for playback too.

## Installation

Dedicated server:

- Install LazoDiscs on the server.
- Install Plasmo Voice on the server.
- Players need Plasmo Voice on the client to hear discs.
- Players do not need LazoDiscs on the client.

Singleplayer:

- Install LazoDiscs locally.
- Install Plasmo Voice locally.
- Install Fabric API too when using Fabric.

## Building

```text
./gradlew build
```

Use the normal jar from `build/libs`. Do not use `-dev`, `-dev-shadow`, `-sources`, or old `-thin` jars.

## Credits

Made by EyeCrasher.

Based on the original Plasmo Voice Discs addon.

LazoDiscs is not affiliated with or endorsed by the Plasmo Voice developers.
