# Changelog

## 1.0.1+mc1.21.11

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