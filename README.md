# LazoDiscs

LazoDiscs is a server-side NeoForge addon for Plasmo Voice that lets players burn custom music links onto vanilla music discs and play them through positional voice audio.

LazoDiscs is based on the original Plasmo Voice Discs addon and continues as its own mod-loader project.

## Supported versions

Current release: `1.0.0`

Published builds:

- Minecraft `1.21.1`
- Minecraft `1.21.3` - `1.21.11`

## Features

- `/lazodisc burn <url> [title]`
- `/lazodisc erase`
- `/lazodisc search "<song name or link>" [page]`
- `/lazodisc stopall`
- Clickable search results and page navigation in chat
- YouTube and YouTube Music search through LavaPlayer
- SoundCloud and other LavaPlayer-supported sources
- Spotify track links resolved through metadata and matched through YouTube Music
- Direct audio URL support through LavaPlayer
- English server messages for the first rewrite builds
- Separate Plasmo Voice source line for disc volume
- Direct streaming for LavaPlayer sources instead of decoding full tracks into RAM
- Stable one-playback-per-jukebox lifecycle to protect TPS from right-click/eject spam
- Sable / Create Aeronautics moving platform position support
- Requires Plasmo Voice wherever LazoDiscs is installed
- Server-side on dedicated servers: players need Plasmo Voice, not LazoDiscs

## Commands

```text
/lazodisc burn <url> [title]
/lazodisc erase
/lazodisc search "<song name or link>" [page]
/lazodisc stopall
```

`/lazodisc search` accepts song names, Spotify track links, and direct playable links. Spotify is resolved as metadata and matched through YouTube Music.

## Server config

The common config contains the main server-side options.

```toml
language = "en_us"

[lavaplayer]
maxConcurrentAudioLoads = 3
maxStreamingTrackLengthSeconds = 0
```

Config path: `config/lazodiscs/config.toml`

Language files path: `config/lazodiscs/lang/<language>.toml`

The built-in English file is created as `config/lazodiscs/lang/en_us.toml` on first start. Server owners can edit it or add another file such as `ru_ru.toml`, then set `language = "ru_ru"`.

`maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length.

## Singleplayer

On a dedicated server, LazoDiscs is server-side: players do not need the mod installed locally, only Plasmo Voice.

Singleplayer uses an integrated server inside the client. For singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

## Notes

- The public cache command was removed.
- The legacy RAM cache/preload playback path was removed.
- Global and per-chunk jukebox limits were removed.
- LavaPlayer sources stream directly into Plasmo Voice.
- Apache HttpClient is relocated in the shaded jar to avoid Java module split-package crashes.
- Jackson is bundled and relocated for Minecraft 1.21.11 because lavalink-youtube needs `JsonNode` at runtime.
