# LazoDiscs

LazoDiscs is a server-side NeoForge mod for Minecraft 1.21.1 that lets players burn custom audio links onto vanilla music discs and play them through Plasmo Voice positional audio.

## Features

- `/lazodiscs burn <url> [title]`
- `/lazodiscs clear`
- `/lazodiscs search "<song name>" [page]`
- Clickable search results in chat
- Clickable chat arrows ◀ ▶ for search pages
- Separate Plasmo Voice source line for disc volume
- YouTube / YouTube Music search through LavaPlayer
- SoundCloud support through LavaPlayer
- Spotify links resolved through metadata and matched through YouTube Music search
- Direct MP3 support
- Sable / Create Aeronautics moving platform position support
- Server-side only: clients need Plasmo Voice, not LazoDiscs

## Version

1.0.0


## Singleplayer support

LazoDiscs remains server-side for multiplayer servers: players do not need LazoDiscs installed to join a dedicated server that has it.

Singleplayer is different because the integrated server runs inside the client. To use LazoDiscs in singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

This does not make LazoDiscs required on multiplayer clients because `displayTest="IGNORE_ALL_VERSION"` is still used.

## Build note

Apache HttpClient is relocated in the shaded jar to avoid Java module split-package crashes while keeping LavaPlayer/youtube-source working.
