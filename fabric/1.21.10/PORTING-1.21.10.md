# LazoDiscs 1.0.0 for Minecraft 1.21.10

Target:

- Minecraft: 1.21.10
- NeoForge: 21.10.63
- Parchment: disabled for this port
- Plasmo Voice API: 2.1.8
- Output jar: `lazodiscs-1.0.0+mc1.21.10.jar`

Use Plasmo Voice `neoforge-1.21.9-2.1.8`, which supports Minecraft 1.21.9–1.21.10.

Fixes kept:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson.
- Chat click/hover events use `ClickEvent.SuggestCommand`, `ClickEvent.RunCommand`, and `HoverEvent.ShowText`.
- `CompoundTag` getters use `Optional`.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.
- `JukeboxBlockEntity#loadAdditional` mixin uses `ValueInput`.
- `ServerPlayer#getServer()` usage is replaced with `CommandSourceStack#getServer()`.
- No direct `FMLEnvironment.dist` usage.

Do not use the `-thin` jar.

Note: Parchment is disabled because `org.parchmentmc.data:parchment-1.21.10:2025.11.16` is not published.
