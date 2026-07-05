# Platform Matrix

This matrix is a planning note for the rewrite branch. It tracks what should be built first and what depends on Plasmo Voice publishing compatible mod builds.

## Upstream Baseline

`plasmoapp/pv-addon-discs` currently publishes a Paper/Folia/Purpur plugin. Its source uses a shared core, a Paper plugin module, and version-specific NMS modules.

For LazoDiscs, copy the idea, not the Paper-only implementation:

- one shared playback/service core
- thin platform adapters for commands, config, disc data, jukebox lifecycle, and Plasmo Voice bootstrap
- version-specific code only where Minecraft internals differ

## Loader Scope

- NeoForge: first-class target.
- Fabric: first-class target.
- Forge: first-class target for versions where Plasmo Voice publishes Forge builds.
- Quilt: target through Fabric-compatible packaging first, then a separate Quilt adapter only if runtime testing shows it is needed.

Quilt is listed in the product target because players ask for it, but the current Plasmo Voice Modrinth listings checked for the rewrite did not show separate Quilt loader artifacts. Treat this as a validation item before public upload.

## First Build Order

1. Pick one Minecraft version with the broadest current Plasmo Voice mod-loader coverage.
2. Build all supported loaders for that version.
3. Run jukebox spam/eject/chunk-unload tests on that version.
4. Port the same structure to the next Minecraft version.

For the first pass, prefer `1.21.1` because it has existing LazoDiscs history and Plasmo Voice publishes NeoForge, Forge, and Fabric builds for the `1.21` / `1.21.1` line.

## Known Version Notes

- Official pv-addon-discs supports many server versions through Paper/Folia/Purpur, including old lines and the latest 1.21.x line.
- Plasmo Voice mod builds are more specific by loader and Minecraft version, so LazoDiscs mod-loader publishing must follow Plasmo Voice mod artifacts, not only the Paper plugin version list.
- `1.21.2` can likely be covered by the Plasmo Voice `1.21.3` compatibility line, but it still needs runtime testing before upload.

## Current Non-Goals

- No Yandex Music support.
- No VK Music support.
- No goat horn support.
- No legacy burned-disc compatibility.
