# LazoDiscs Rewrite Plan

This document tracks the current LazoDiscs rewrite from our side.

LazoDiscs is based on the original Plasmo Voice Discs addon and continues as its own mod-loader project.

## Direction

- Keep public version continuity from the published `1.0.0` release.
- Keep only music disc playback.
- Use `/lazodisc` as the public command root.
- Use a new custom disc data format.
- Start with English messages.
- Keep the server-side dedicated-server experience where possible.

## Current Changes

- Search accepts song names, Spotify track links, and direct playable links.
- Spotify track links are resolved as metadata and matched through YouTube Music.
- Playback streams through LavaPlayer directly into Plasmo Voice.
- The RAM cache and preload playback path were removed.
- Jukebox playback is owned per block, so replacing, ejecting, breaking, unloading, or invalidating a jukebox stops the current playback cleanly.
- Global jukebox limits are removed.
- Yandex Music and VK Music are not part of the current scope.
- Goat horns are not part of the current scope.
- Old burned discs do not need compatibility.

## Target Services

- YouTube and YouTube Music.
- Spotify track links through metadata matching.
- SoundCloud and other stable LavaPlayer-supported sources.
- Direct playable audio URLs supported by LavaPlayer.

## Target Loaders

- NeoForge.
- Fabric.
- Forge.
- Quilt, through Fabric-compatible packaging first unless runtime testing shows that a separate adapter is needed.

## First Milestone

1. Build one Minecraft version across supported loaders.
2. Test `/lazodisc burn`, `/lazodisc erase`, and `/lazodisc search`.
3. Test jukebox insert, eject, replacement, break, and chunk unload.
4. Test long-track playback without RAM spikes.
5. Port the same structure to the next Minecraft version.
