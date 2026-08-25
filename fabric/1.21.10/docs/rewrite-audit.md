# LazoDiscs Rewrite Audit

This document tracks the rewrite from the current single-loader NeoForge mod into a cleaner Plasmo Voice discs addon for NeoForge, Fabric, Forge, and Quilt.

## Product Direction

- Keep the public project continuity from the published `1.0.0` release.
- Rewrite the internals instead of patching the existing code in place.
- Use `plasmoapp/pv-addon-discs` as the main reference for playback lifecycle and LavaPlayer streaming.
- Keep only music discs. Do not port goat horn support.
- Use `/lazodisc` commands, matching the upstream command shape but not the `/disc` literal.
- Use a new custom disc data format. Old burned discs do not need compatibility.
- English only for the first rewritten builds. Add other languages later.

## Checked Issues

### LazoDiscs

- `EyeCrasher07/LazoDiscs` currently has no GitHub issues.

### pv-addon-discs

- `#33 Add support for Fabric` is open. This confirms demand for a mod-loader implementation.
- `#94 Multiple discs can be played at the same time from the same jukebox` is closed. The fix direction is job ownership per jukebox plus cancellation on replacement/eject/hopper/break/chunk unload.
- `#107 No matches found` is open. HTTP source whitelist and stream playlist handling need clear errors.
- `#112 New supported sources` is open and explicitly requests YandexMusic/VKMusic. This was checked, but Yandex/VK are out of scope for the current rewrite.
- `#118 No sound after 10 to 20 seconds` and `#126 Music stops playing with a warn` point at Bukkit/hybrid-server record-state problems. Do not copy Bukkit record reset logic into mod loaders blindly.
- `#130 The problem with playing music from YouTube` is open. YouTube remains the main unstable source, so config needs oauth2/poToken/remoteCipher/client controls and user-facing error messages.
- `#132 [music support] 163music` is open. The resolver design should allow adding more metadata/search providers later.
- `#133 Error on /disc burn` is closed in upstream `1.1.11`; keep the latest upstream command fixes in mind.

## Upstream Playback Lessons

The upstream addon does not primarily solve jukebox spam with global limits. It solves it with lifecycle ownership:

- One active playback job per jukebox block.
- Replacing a disc cancels the old job before starting a new one.
- Eject, hopper pull, break, explosion, and chunk unload cancel the job.
- Playback streams through LavaPlayer into a Plasmo Voice `AudioFrameProvider`.
- The LavaPlayer `AudioPlayer` and Plasmo source are destroyed/removed when the sender stops.
- Cleanup checks that the job being cleaned up is still the current job for that jukebox before changing block state.

For LazoDiscs, keep this model. Add cooldowns or limits only if mod-loader events create a reproducible problem that ownership/cancellation does not solve.

## Target Services

- YouTube / YouTube Music through LavaPlayer and youtube-source.
- SoundCloud and other stable LavaPlayer sources.
- Direct HTTP/audio URLs.
- Spotify links through metadata resolution and YouTube Music matching.
- Yandex Music and VK Music are not part of the current scope.

`/lazodisc search` remains one command. It should accept normal search text and service links, then route internally.

## First Engineering Milestone

Create a clean core that can be reused by loaders:

- `core` module: service resolution, LavaPlayer source setup, track loading, playback session lifecycle, command model, config model.
- Loader modules: NeoForge, Fabric, Forge, Quilt adapters for commands, item data, jukebox events, chunk/block lifecycle, Plasmo Voice bridge, and platform config paths.
- Start with one Minecraft version and all loaders for that version, then move to the next version.

The first implementation target is a minimal playable disc flow:

1. `/lazodisc burn <url> [name]`
2. `/lazodisc erase`
3. insert/eject custom disc in a jukebox
4. stream LavaPlayer audio through Plasmo Voice
5. cleanly stop on eject/break/chunk unload
