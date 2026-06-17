# LazoDiscs

**LazoDiscs** is a server-side NeoForge mod for Minecraft 1.21.1 that lets players burn custom audio links onto vanilla music discs and play them through **Plasmo Voice** positional audio.

It is mainly made for modded servers that want custom music discs without requiring a client-side LazoDiscs installation.

## Features

* Custom URL music discs
* Server-side only LazoDiscs mod
* Uses **Plasmo Voice** for positional audio
* Separate Plasmo Voice source line for disc volume
* Search system with clickable chat results
* Chat page arrows for search results
* Direct MP3 support
* YouTube / YouTube Music support
* SoundCloud support
* Spotify link matching through metadata/search
* Sable / Create Aeronautics moving platform position support
* No yt-dlp, spotDL, or ffmpeg required

## Requirements

* Minecraft `1.21.1`
* NeoForge
* Plasmo Voice

Players do **not** need LazoDiscs on the client, but they still need **Plasmo Voice** installed to hear the audio.

Plasmo Voice links:

* Modrinth: https://modrinth.com/plugin/plasmo-voice
* GitHub: https://github.com/plasmoapp/plasmo-voice

## Commands

### Burn a disc

```mcfunction
/lazodiscs burn <url> [title]
```

Example:

```mcfunction
/lazodiscs burn "https://files.example.com/song.mp3" My Song
```

Hold a vanilla music disc in your main hand, then run the command.

### Clear a disc

```mcfunction
/lazodiscs clear
```

Removes LazoDiscs data from the disc in your hand.

### Search music

```mcfunction
/lazodiscs search "<song name>"
```

Example:

```mcfunction
/lazodiscs search "Afterglow"
```

Search results appear in chat.

Click a song title to automatically fill the burn command.

Use the chat arrows to switch pages:

```text
◀  Page 1/4  ▶
```

## Supported sources

* Direct MP3 links
* YouTube
* YouTube Music
* SoundCloud
* Spotify links through metadata/search matching

## Spotify support

Spotify links are not played directly, because Spotify does not expose a normal direct audio stream from public track URLs.

LazoDiscs reads Spotify metadata when possible, then searches for a matching track through YouTube Music.

## Plasmo Voice integration

LazoDiscs uses **Plasmo Voice** for positional audio playback and registers its own Plasmo Voice source line for discs.

This allows players to control disc volume separately from other Plasmo Voice sources.

The source line name can be changed in the server config.

Plasmo Voice:

* Modrinth: https://modrinth.com/plugin/plasmo-voice
* GitHub: https://github.com/plasmoapp/plasmo-voice

## Server-side only

LazoDiscs is designed to be installed on the server only.

Client requirements:

* Plasmo Voice

Server requirements:

* NeoForge
* Plasmo Voice
* LazoDiscs

## Config

The config file is generated on the server:

```text
config/lazodiscs-common.toml
```

Useful options include:

```toml
sourceLineName = "Пластинки"
nowPlayingMessage = "Сейчас играет: %title%"
preloadOnBurn = true
maxCachedTracks = 4
```

## Building

Linux/macOS:

```bash
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

The built jar will be in:

```text
build/libs/
```

## Credits

Made by **EyeCrasher**.

Uses **Plasmo Voice** for positional audio playback.

LazoDiscs is not affiliated with or endorsed by the Plasmo Voice developers.
