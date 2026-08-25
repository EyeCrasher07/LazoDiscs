# LazoDiscs 1.0.0 for Minecraft 1.21.9

Target:

- Minecraft: 1.21.9
- NeoForge: 21.9.16-beta
- Parchment: 1.21.9 / 2025.10.05
- Plasmo Voice API: 2.1.8
- Output jar: `lazodiscs-1.0.0+mc1.21.9.jar`

Use Plasmo Voice `neoforge-1.21.9-2.1.8`.

Fixes kept:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson.
- Chat click/hover events use `ClickEvent.SuggestCommand`, `ClickEvent.RunCommand`, and `HoverEvent.ShowText`.
- `CompoundTag` getters use `Optional`.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.
- `JukeboxBlockEntity#loadAdditional` mixin uses `ValueInput`.

Do not use the `-thin` jar.

Additional 1.21.9 API fixes:

- `ServerPlayer#getServer()` usage was replaced with `CommandSourceStack#getServer()`.
- Removed direct `FMLEnvironment.dist` usage and load the Plasmo addon through the guarded bootstrap.
