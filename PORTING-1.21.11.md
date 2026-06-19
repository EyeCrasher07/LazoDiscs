# LazoDiscs 1.0.3 for Minecraft 1.21.11

Target:

- Minecraft: 1.21.11
- NeoForge: 21.11.42
- Parchment: disabled for this port
- Plasmo Voice API: 2.1.10
- Output jar: `lazodiscs-1.0.3+mc1.21.11.jar`

Use Plasmo Voice `neoforge-1.21.11-2.1.10`.

Fixes kept:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson.
- Chat click/hover events use `ClickEvent.SuggestCommand`, `ClickEvent.RunCommand`, and `HoverEvent.ShowText`.
- `CompoundTag` getters use `Optional`.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.
- `JukeboxBlockEntity#loadAdditional` mixin uses `ValueInput`.
- `ServerPlayer#getServer()` usage is replaced with `CommandSourceStack#getServer()`.
- No direct `FMLEnvironment.dist` usage.
- Dimension key location calls are updated to identifier calls for the 1.21.11 naming changes.

Do not use the `-thin` jar.

Additional 1.21.11 API fix: `CommandSourceStack#hasPermission(...)` was replaced with `hasPermissionLevel(...)`.

Additional 1.21.11 API fix: command permission check uses reflection because `CommandSourceStack` permission methods changed.

Additional runtime fix:

- Jackson is now bundled and relocated into the mod jar.
- This fixes youtube-source failing on client/server with missing `com.fasterxml.jackson.databind.JsonNode`.

1.0.3 changes:

- Version metadata updated to `1.0.3+mc1.21.11`.
- Broken Russian config defaults and broken search chat glyphs fixed.
- Audio loading now uses a configurable bounded executor instead of one unmanaged thread per load/search.
- `maxActiveSources=0` keeps active jukebox playback unlimited by default.
- Added `/lazodiscs stopall`, `/lazodiscs cache stats`, and `/lazodiscs cache clear`.
