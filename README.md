# LazoDiscs

LazoDiscs lets players burn custom music links onto vanilla music discs and play them through Plasmo Voice positional audio.

[![GitHub release](https://img.shields.io/github/v/release/EyeCrasher07/LazoDiscs?label=release)](https://github.com/EyeCrasher07/LazoDiscs/releases)
[![Modrinth](https://img.shields.io/modrinth/dt/lazodiscs?label=modrinth)](https://modrinth.com/mod/lazodiscs)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

## Download

- [GitHub Releases](https://github.com/EyeCrasher07/LazoDiscs/releases)
- [Modrinth](https://modrinth.com/mod/lazodiscs)

## Requirements

- Minecraft `1.21.1` - `1.21.11`
- NeoForge
- Plasmo Voice

## Features

- Burn music links onto vanilla music discs.
- Search tracks from chat and burn them from clickable results.
- Play discs through Plasmo Voice positional audio.
- Supports YouTube, YouTube Music, SoundCloud, Spotify track links, and direct playable audio URLs.
- Streams playback directly through LavaPlayer.
- Keeps one active playback per jukebox and cleans it up automatically.
- Supports Sable / Create Aeronautics moving platform positions.
- Server owners can edit all messages through language files.

## Commands

```text
/lazodisc burn <url> [title]
/lazodisc erase
/lazodisc search <song name>
```

`/lazodisc search` accepts song names only. Use `/lazodisc burn` for Spotify, YouTube, SoundCloud, or direct playable links.

## Server Config

Config file:

```text
config/lazodiscs/config.toml
```

Language files:

```text
config/lazodiscs/lang/<language>.toml
```

Default config:

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

The built-in English file is created as `config/lazodiscs/lang/en_us.toml` on first start. Server owners can edit it or add another file such as `ru_ru.toml`, then set `language = "ru_ru"`.

`maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length.

`requirePermissionForPlay = false` means players can play already-burned discs by default. Server owners can set it to `true` to require operator permission for playback too.

## Singleplayer

On a dedicated server, install LazoDiscs on the server. Players need Plasmo Voice to hear discs, but they do not need LazoDiscs installed locally.

Singleplayer uses an integrated server inside the client. For singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

## Building

```text
./gradlew build
```

Use the normal jar from `build/libs`. Do not use the `-thin` jar.

## Credits

Based on the original Plasmo Voice Discs addon.
