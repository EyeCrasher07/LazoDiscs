# LazoDiscs

LazoDiscs is a server-side NeoForge addon for Plasmo Voice that lets players burn custom music links onto vanilla music discs and play them through positional voice audio.

## Supported Versions

Current release: `1.0.1`

Minecraft versions:

- `1.21.1` - `1.21.11`

## Features

- `/lazodisc burn <url> [title]`
- `/lazodisc erase`
- `/lazodisc search <song name>`
- Clickable search results in chat
- YouTube and YouTube Music search through LavaPlayer
- SoundCloud and other LavaPlayer-supported sources
- Spotify track links resolved through metadata and matched through YouTube Music
- Direct audio URL support through LavaPlayer
- Track validation before discs are burned
- Separate Plasmo Voice source line for disc volume
- Direct streaming for LavaPlayer sources
- Stable one-playback-per-jukebox lifecycle
- Separate permission settings for burn, erase, search, and play
- Server-side hiding of the original vanilla music disc tooltip on burned discs
- Sable / Create Aeronautics moving platform position support
- Requires Plasmo Voice wherever LazoDiscs is installed
- Server-side on dedicated servers: players need Plasmo Voice, not LazoDiscs

## Commands

```text
/lazodisc burn <url> [title]
/lazodisc erase
/lazodisc search <song name>
```

`/lazodisc search` accepts song names only. Use `/lazodisc burn` for Spotify, YouTube, SoundCloud, or direct playable links.

## Server Config

The common config contains the main server-side options.

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

Config path: `config/lazodiscs/config.toml`

Language files path: `config/lazodiscs/lang/<language>.toml`

The built-in English file is created as `config/lazodiscs/lang/en_us.toml` on first start. Server owners can edit it or add another file such as `ru_ru.toml`, then set `language = "ru_ru"`.

`maxStreamingTrackLengthSeconds = 0` means streamed LavaPlayer tracks are unlimited by length.

`requirePermissionForPlay = false` means players can play already-burned discs by default. Server owners can set it to `true` to require operator permission for playback too.

## Singleplayer

On a dedicated server, LazoDiscs is server-side: players do not need the mod installed locally, only Plasmo Voice.

Singleplayer uses an integrated server inside the client. For singleplayer, install LazoDiscs in the local client's `mods` folder together with Plasmo Voice.

## Notes

- The public cache command was removed.
- The legacy RAM cache/preload playback path was removed.
- Global and per-chunk jukebox limits were removed.
- LavaPlayer sources stream directly into Plasmo Voice.
- `/lazodisc burn` checks that LavaPlayer can resolve the track before writing the disc.
- Finished or failed playback sources are removed from the active jukebox map automatically.
- Apache HttpClient is relocated in the shaded jar to avoid Java module split-package crashes.
- Jackson is bundled and relocated where needed for LavaPlayer YouTube support.
