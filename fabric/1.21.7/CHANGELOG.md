# LazoDiscs Changelog

## 1.0.5 — Unreleased

### Fixed

- Enforce configured burn, erase, search and playback permission levels, including the Minecraft 1.21.11 permission API. Failed permission lookups no longer grant access.
- Keep asynchronous burning bound to the original, unchanged disc and recheck permissions before writing the result.
- Validate stored URLs before audio loading, including Spotify URI conversion and configured scheme/domain restrictions.
- Prevent late callbacks and concurrent start/stop operations from stopping a replacement source or leaving audio resources behind.
- Drain buffered audio frames before finishing a track.
- Resume saved Fabric and NeoForge jukeboxes after chunk load when Plasmo Voice is ready; clear pending work on unload and server shutdown. NeoForge waits for fully loaded chunks without forcing loads or restarting an identical active track.
- Avoid consuming a disc before validating the jukebox block entity.
- Allow ordinary far-away jukeboxes when Sable is not installed.
- Close Spotify HTTP connections on success and failure.
- Register NeoForge client tooltip handling and reject non-finite Fabric volume settings.
- Validate language identifiers to prevent language files escaping their configuration directory; invalid values fall back to `en_us`.
- Stop Sophisticated backpack playback sources when the Plasmo Voice addon shuts down.

### Improved

- Bound pending audio-load requests; rejected requests show a localized retry message and finish playback callbacks instead of leaving them waiting.
- Cancel queued work during shutdown and keep executor reconfiguration restart-safe.
- Update LavaPlayer to 2.2.7 and the pinned YouTube-source snapshot to 2be8e542d3f6f178e048dca565892684c2e40177.
- Update privately relocated HttpClient to 4.5.14 and Jackson through BOM 2.21.7 without replacing Minecraft's library versions.
- Update bundled Rhino/rhino-engine to 1.7.15.1 and JSON-java to 20260719 for upstream security fixes, keeping these audio libraries off Minecraft's compile classpath.
- Align dependency metadata, resource/data pack formats and addon version identifiers across all source ports.
- Standardize Java formatting and improve lifecycle/compatibility comments.
- Add behavioral regressions, project/resource checks, full-matrix CI, reproducible archive settings and release packaging checks.
- Preserve complete bundled third-party licenses/notices, including colliding Jackson, Apache, Jsoup and Rhino resources, and verify their contents in final release JARs.

### Compatibility

Java 21; Plasmo Voice 2.1.8 or newer. Install matching Minecraft/loader builds. LazoBoombox 0.1.2 requires LazoDiscs 1.0.5 or newer.

Source ports exist for Minecraft 1.21.1–1.21.11 on Fabric and NeoForge. Official Plasmo Voice 2.1.17 metadata covers 1.21.1, 1.21.4, 1.21.6–1.21.8 and 1.21.11. Other ports require a separately compatible Plasmo Voice build; compilation alone does not establish runtime support.


---

## 1.0.4 — 2026-09-12

### Fixed
- **Sable platforms — backpack (Jukebox Upgrade):** audio source no longer stays frozen
  at the original world position after the platform is assembled. The source now stops
  within ≤5 ticks of assembly (detected via block-state air check in the server-tick poller).

- **Sable platforms — boombox position projection:** replaced the early-exit guard in
  `BoomboxSableCompat.project()` (which skipped the Sable API for coordinates within
  ±1 000 000) with `projectBoomboxCenter()` that always calls
  `Sable.HELPER.projectOutOfSubLevel()`, matching `SablePositionCompat.projectJukeboxCenter()`.

- **Sable platforms — immediate playback restart:** boombox now overrides `onLoad()` so
  playback resumes in the same tick Sable places the block entity at its new position,
  instead of waiting up to 20 ticks for `serverTick`.

### Added
- **Configurable command alias:** `commandAlias` option in config. Admins can rename
  `/lazodisc` to any string (e.g. `disc`, `music`, `lazodiscs`). Requires a server restart.
  Clickable `/burn` suggestions in search results update automatically to match.

---

## 1.0.3 — initial public release
