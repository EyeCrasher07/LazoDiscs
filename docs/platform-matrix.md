# Platform Matrix

This matrix tracks LazoDiscs loader targets and publishing order.

LazoDiscs is based on the original Plasmo Voice Discs addon and is being developed as a mod-loader project.

## Loader Scope

- NeoForge: first-class target.
- Fabric: first-class target.
- Forge: first-class target where Plasmo Voice provides Forge builds.
- Quilt: target through Fabric-compatible packaging first, then a separate adapter only if testing shows it is needed.

## First Build Order

1. Start with one Minecraft version that has broad Plasmo Voice loader support.
2. Build all supported loaders for that Minecraft version.
3. Run playback, search, Spotify, eject-spam, break, and chunk-unload tests.
4. Move to the next Minecraft version after the first version is stable.

For the first pass, prefer `1.21.1` because it has existing LazoDiscs release history and broad Plasmo Voice loader coverage.

## Version Notes

- LazoDiscs releases should follow Plasmo Voice mod-loader availability.
- `1.21.2` may be covered by the Plasmo Voice `1.21.3` compatibility line, but it still needs runtime testing before upload.

## Current Non-Goals

- No Yandex Music support.
- No VK Music support.
- No goat horn support.
- No legacy burned-disc compatibility.
