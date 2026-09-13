# LazoDiscs Changelog

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

