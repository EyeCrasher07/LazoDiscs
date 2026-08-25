# LazoDiscs 1.0.0 for Minecraft 1.21.6

Target:

- Minecraft: 1.21.6
- NeoForge: 21.6.20-beta
- Parchment: 1.21.6 / 2025.06.29
- Plasmo Voice API: 2.1.10
- Output jar: `lazodiscs-1.0.0+mc1.21.6.jar`

Fixes kept:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson.
- Chat click/hover events use `ClickEvent.SuggestCommand`, `ClickEvent.RunCommand`, and `HoverEvent.ShowText`.
- `CompoundTag` getters use `Optional`.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.

Do not use the `-thin` jar.

Runtime fix: `JukeboxBlockEntity#loadAdditional` now uses `ValueInput`, so the mixin injection descriptor was updated.
