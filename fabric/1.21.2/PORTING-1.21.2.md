# LazoDiscs 1.0.0 for Minecraft 1.21.2

Target:

- Minecraft: 1.21.3
- NeoForge: 21.2.1-beta
- Parchment: disabled for this port
- Plasmo Voice API: 2.1.8
- Output jar: `lazodiscs-1.0.0+mc1.21.2.jar`

Do not use the `-thin` jar.

Build fix: LavaPlayer Jackson dependencies are excluded because Minecraft 1.21.2 strictly pins Jackson 2.13.x.

API fix: `ItemInteractionResult` was changed to `InteractionResult` for the jukebox insertion hook on this port.

API fix: `InteractionResult.sidedSuccess(boolean)` is not present in 1.21.3 mappings. The port returns `InteractionResult.SUCCESS` on the client and `InteractionResult.SUCCESS_SERVER` on the server.
