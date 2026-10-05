# Release checklist

This repository prepares files for manual publication. No token, upload, push or public release is needed for local verification.

## Verify the source

Use Java 21 and Node.js 22 or newer, then run from the repository root:

```sh
node tools/verify-projects.mjs
node tools/format-java.mjs --check
node tools/test-release-tools.mjs
node tools/test-jukebox-resync.mjs
node tools/test-bundled-notices.mjs
node tools/build-all.mjs
git diff --check
```

Run the behavioral regression command listed in the root README as well. Each Minecraft/loader directory is an independent project; do not copy classes between versions without checking the target API.

## Verify in Minecraft

Before calling this a stable release, test a dedicated server and an integrated singleplayer server with the matching Plasmo Voice and Fabric API/NeoForge builds. At minimum, cover the oldest and newest release targets on both loaders. Compilation does not verify audio delivery, mixins, rendering or optional-mod integration.

- Burn/search as an ordinary player and an operator; verify the configured permission levels.
- Start a slow burn, switch the held disc, and confirm another item is not modified.
- Play, replace, remove and rapidly restart a custom disc; listen through the track's final frames.
- Unload/reload a chunk, restart the server, disconnect and die during playback.
- Save a custom disc in a jukebox inside the spawn chunks, restart the server and confirm playback resumes after Plasmo Voice initializes without ejecting/reinserting the disc.
- Check invalid/disallowed URLs and network/service failures without repeated chat spam.
- With LazoBoombox installed on both sides: test main/offhand playback, HUD position, insertion/removal, ownership, Shift+right-click pickup, breaking and explosions with an inserted disc.
- Test `doTileDrops=false`, creative mode and `placed_boombox=false`.
- If advertising support for Sable/Create Aeronautics or Sophisticated Backpacks, test assembly/disassembly and audio position in the exact mod versions being advertised.

Record Minecraft, loader, Plasmo Voice and optional-mod versions with the result. Do not label untested optional integrations as verified.

## Prepare files

Install `unzip`, then run:

```sh
node tools/prepare-release.mjs
```

The output in `dist/<version>/publish-candidates/` contains normal release JARs, the copy-ready English changelog, SHA-256 checksums and a per-file manifest. Source/development/thin JARs are not upload files.

The default selection includes only targets covered by the documented Plasmo Voice release metadata. This is availability, not an in-game test result. To collect every source port for development or a separately verified custom Plasmo Voice build:

```sh
node tools/prepare-release.mjs --all-targets
```

Never advertise the other Minecraft ports as working solely because they compile.

## Modrinth and CurseForge

For each JAR, select its **exact** Minecraft version and loader. Use the version number from the manifest; a display name can include both loader and Minecraft version. Paste `release-notes/<version>.md` (or the Russian variant).

Declare Plasmo Voice as a required dependency and Fabric API as required for Fabric. LazoBoombox additionally requires LazoDiscs 1.0.5 or newer on both server and client. LazoDiscs alone can be server-only, with Plasmo Voice installed by listeners. Optional integrations are not required dependencies.

Use a stable release type only after the game checklist passes. Keep source code for the distributed build available, include the existing GPL-3.0-only license, and preserve bundled third-party notices.

All source ports verify upstream legal-resource preservation in their final
release JAR as part of `check`. See [third-party provenance](../third-party/README.md)
for the supplemental upstream copies and exact sources; keep this inventory
current when updating the bundled audio libraries.

## GitHub

Review and integrate the feature-branch changes yourself. The CI checks pull requests and builds all 22 projects. Tag names are `lazodiscs-<version>` and `lazoboombox-<version>` respectively; only create/push a tag after integration and runtime verification.

The tag workflow creates a **draft** GitHub release with all 22 checked ordinary JARs and the prepared changelog, unless a release for that tag already exists. Existing manually prepared releases are left unchanged. Review its supported targets and publish the draft manually. It does not upload anything to Modrinth or CurseForge.

For this release the maintainer reported testing every Minecraft/loader target in game. The manual publication uses all 22 targets from `--all-targets`, not just the conservative 12-target selection based on the reference PV release. Keep matching older PV releases available where its latest release does not support a Minecraft target.
