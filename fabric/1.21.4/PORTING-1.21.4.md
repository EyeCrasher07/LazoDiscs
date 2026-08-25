# LazoDiscs 1.0.0 for Minecraft 1.21.4

Target:

- Minecraft: 1.21.4
- NeoForge: 21.4.137
- Parchment: 1.21.4 / 2025.03.23
- Plasmo Voice API: 2.1.10
- Output jar: `lazodiscs-1.0.0+mc1.21.4.jar`

Build fixes kept from the 1.21.3 port:

- LavaPlayer Jackson dependencies are excluded because Minecraft/NeoForge strictly pins Jackson 2.13.x.
- `JukeboxPlayableMixin` uses `InteractionResult.SUCCESS` / `InteractionResult.SUCCESS_SERVER`.

Do not use the `-thin` jar.
