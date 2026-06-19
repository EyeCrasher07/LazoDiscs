# Changelog

## 1.0.0+mc1.21.9

### Fixed
- Fixed broken Russian default text in the common config.
- Fixed broken search result separator and navigation glyphs in chat.

### Added
- Added bounded audio load executor controlled by `maxConcurrentAudioLoads`.
- Added `/lazodiscs stopall`.
- Added `/lazodiscs cache stats`.
- Added `/lazodiscs cache clear`.

### Changed
- `maxActiveSources` defaults to `0`, meaning unlimited active jukeboxes.
- `maxActiveSourcesPerChunk` defaults to `0`, meaning unlimited active jukeboxes per chunk.

## 1.0.1+mc1.21.9

### Fixed
- Fixed severe TPS drops when many jukeboxes with custom discs are active at the same time.
- Added global and per-chunk safety limits for active LazoDiscs audio sources.
- Reduced unnecessary repeated audio source position updates for normal stationary jukeboxes.
- Fixed music lagging behind Sable / Create Aeronautics flying platforms by keeping dynamic platform audio position updates smooth.
- Reduced repeated vanilla record stop packet spam when inserting custom discs.
- Reduced active jukebox validation overhead by checking state less often.

### Added
- Added `maxActiveSources` config option.
- Added `maxActiveSourcesPerChunk` config option.
- Added `positionUpdateIntervalTicks` config option.
- Added `validationIntervalTicks` config option.

### Recommended server config

``toml
maxActiveSources = 8
maxActiveSourcesPerChunk = 2
positionUpdateIntervalTicks = 10
validationIntervalTicks = 20
``