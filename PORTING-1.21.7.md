# LazoDiscs 1.0.0 for Minecraft 1.21.7

Target:

- Minecraft: 1.21.7
- NeoForge: 21.7.18-beta
- Parchment: 1.21.7 / 2025.07.18
- Plasmo Voice API: 2.1.10
- Output jar: `lazodiscs-1.0.0+mc1.21.7.jar`

Fixes kept:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson.
- Chat click/hover events use `ClickEvent.SuggestCommand`, `ClickEvent.RunCommand`, and `HoverEvent.ShowText`.
- `CompoundTag` getters use `Optional`.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.
- `JukeboxBlockEntity#loadAdditional` mixin uses `ValueInput`.

Do not use the `-thin` jar.
