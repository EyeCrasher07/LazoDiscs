# LazoDiscs

LazoDiscs is a server-side NeoForge addon for Plasmo Voice that lets players burn custom music links onto vanilla music discs and play them through positional voice audio.

## Supported versions

Current release: `1.0.0`

Published builds:

- Minecraft `1.21.1`
- Minecraft `1.21.3` - `1.21.11`

## Features

- `/lazodiscs burn <url> [title]`
- `/lazodiscs clear`
- `/lazodiscs search "<song name>" [page]`
- `/lazodiscs stopall`
- Clickable search results and page navigation in chat
- YouTube and YouTube Music search through LavaPlayer
- SoundCloud and other LavaPlayer-supported sources
- Spotify track links resolved through metadata and matched through YouTube Music
- Direct MP3 support
- Russian and English server messages via `display.language`
- Separate Plasmo Voice source line for disc volume
- Direct streaming for LavaPlayer sources instead of decoding full tracks into RAM
- Jukebox restart cooldown to protect TPS from right-click/eject spam
- Sable / Create Aeronautics moving platform position support
- Server-side on dedicated servers: players need Plasmo Voice, not LazoDiscs

## Commands

```text
/lazodiscs burn <url> [title]
/lazodiscs clear
/lazodiscs search "<song name>" [page]
/lazodiscs stopall
```

`/lazodiscs search` is for song names only, not links. To burn a Spotify, YouTube, SoundCloud, or direct audio link, use `/lazodiscs burn`.

## Server config

The common config contains the main server-side options.

```toml
[display]
language = "ru_ru"
sourceLineName = "auto"
nowPlayingMessage = "auto"

[audio]
maxConcurrentAudioLoads = 3
maxTrackLengthSeconds = 900
maxStreamingTrackLengthSeconds = 0
jukeboxRestartCooldownTicks = 40
```

Use `language = "ru_ru"` for Russian messages or `language = "en_us"` for English messages.

`maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length. `maxTrackLengthSeconds` still applies to decoded/preloaded fallback audio.

## Singleplayer

On a dedicated server, LazoDiscs is server-side: players do not need the mod installed locally, only Plasmo Voice.

Singleplayer uses an integrated server inside the client. For singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

## Notes

- The public `/lazodiscs cache` command was removed.
- Global and per-chunk jukebox limits were removed.
- LavaPlayer sources stream directly into Plasmo Voice.
- Apache HttpClient is relocated in the shaded jar to avoid Java module split-package crashes.
- Jackson is bundled and relocated for Minecraft 1.21.11 because lavalink-youtube needs `JsonNode` at runtime.
