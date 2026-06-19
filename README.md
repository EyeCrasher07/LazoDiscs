# LazoDiscs

LazoDiscs is a server-side NeoForge mod for Minecraft 1.21.11 that lets players burn custom audio links onto vanilla music discs and play them through Plasmo Voice positional audio.

## Features

- `/lazodiscs burn <url> [title]`
- `/lazodiscs clear`
- `/lazodiscs search "<song name>" [page]`
- Clickable search results in chat
- Clickable chat arrows for search pages
- Separate Plasmo Voice source line for disc volume
- YouTube / YouTube Music search through LavaPlayer
- SoundCloud support through LavaPlayer
- Spotify links resolved through metadata and matched through YouTube Music search
- Direct MP3 support
- Configurable active jukebox source limit. `0` means unlimited.
- Configurable audio load queue to avoid CPU/RAM spikes when many discs start.
- `/lazodiscs stopall`
- `/lazodiscs cache stats`
- `/lazodiscs cache clear`
- Sable / Create Aeronautics moving platform position support
- Server-side only: clients need Plasmo Voice, not LazoDiscs

## Version

1.0.3


## Singleplayer support

LazoDiscs remains server-side for multiplayer servers: players do not need LazoDiscs installed to join a dedicated server that has it.

Singleplayer is different because the integrated server runs inside the client. To use LazoDiscs in singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

This does not make LazoDiscs required on multiplayer clients because `displayTest="IGNORE_ALL_VERSION"` is still used.

## Build note

Apache HttpClient is relocated in the shaded jar to avoid Java module split-package crashes while keeping LavaPlayer/youtube-source working.


## RAM-only preload/cache

LazoDiscs preloads decoded audio into RAM after `/lazodiscs burn`.
When a jukebox starts a disc that is already cached, playback starts almost instantly.

The cache is memory-only and is cleared when the server or client exits.
No decoded audio is written to disk.

## Runtime note

Jackson is bundled and relocated inside LazoDiscs for Minecraft 1.21.11 because lavalink-youtube needs `JsonNode` at runtime.

## 1.0.3 notes

- Fixed broken Russian default text in the common config.
- Fixed broken chat separator/navigation glyphs in search output.
- Added `maxActiveSources`, default `0` for unlimited active jukeboxes.
- Added `maxConcurrentAudioLoads`, default `3`, so many discs do not start unbounded decoder threads.
- Added admin commands for stopping all sources and inspecting/clearing the RAM cache.
