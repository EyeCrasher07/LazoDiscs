# LazoDiscs

LazoDiscs is a server-side NeoForge addon for Plasmo Voice that lets players burn custom music links onto vanilla music discs and play them through positional voice audio.

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
- Direct MP3 support
- English server messages for the first rewrite builds
- Separate Plasmo Voice source line for disc volume
- Direct streaming for LavaPlayer sources instead of decoding full tracks into RAM
- Upstream-style one-playback-per-jukebox lifecycle to protect TPS from right-click/eject spam
- Sable / Create Aeronautics moving platform position support
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
[display]
sourceLineName = "auto"
nowPlayingMessage = "auto"

[lavaplayer]
maxConcurrentAudioLoads = 3
maxStreamingTrackLengthSeconds = 0
```

`maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length. `maxTrackLengthSeconds` still applies to decoded/preloaded fallback audio.

## Singleplayer

On a dedicated server, LazoDiscs is server-side: players do not need the mod installed locally, only Plasmo Voice.

Singleplayer uses an integrated server inside the client. For singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

## Notes

- The public cache command was removed.
- Global and per-chunk jukebox limits were removed.
- LavaPlayer sources stream directly into Plasmo Voice.
- Apache HttpClient is relocated in the shaded jar to avoid Java module split-package crashes.
- Jackson is bundled and relocated for Minecraft 1.21.11 because lavalink-youtube needs `JsonNode` at runtime.
