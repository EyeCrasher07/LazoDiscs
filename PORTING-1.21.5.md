# LazoDiscs 1.0.0 for Minecraft 1.21.5

Target:

- Minecraft: 1.21.5
- NeoForge: 21.5.97
- Parchment: 1.21.5 / 2025.04.19
- Plasmo Voice API: 2.1.8
- Output jar: `lazodiscs-1.0.0+mc1.21.5.jar`

Build fixes kept:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson 2.13.x.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.

Do not use the `-thin` jar.

Additional 1.21.5 API fixes:

- Chat click/hover events now use nested event records such as `ClickEvent.SuggestCommand`, `ClickEvent.RunCommand`, and `HoverEvent.ShowText`.
- `CompoundTag` getters now return `Optional`.
- Removed the old additional-tooltip hide component usage for this port.
