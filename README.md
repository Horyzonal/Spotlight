# Spotlight

The minecraft equivilant of MacOS' spotlight, Windows' Powertools, Raycast, Alfred and Vicinae.

## How use
The default hotkey is `` ` ``. Type in your command to use.

Though most features can be used by the keyboard, there are a few commands for the mouse.

## Features
- **About Spotlight:** made by Horyzon, profile image, personal website, repository, and mod homepage from installed metadata.
- Replaces chat for minecraft /commands (Improvements to the original)
- Text editor (With gradients)
- Banner editor (GUI)
- Crafting recipes (kinda pointless)
- Alternative method of /fill and /clone
- Finding things (blocks, entities, items)
- Locations, like waypoints (syncs with map mods)
- UUID grabber
- Alias commands like gmc, gms
- Quickly displaying F3 informations
- Local native client chest, 41-slot kits and item archive with live fuzzy search
- Head database, player skulls, wiki search and custom enchantments
- Installed NeoForge mods with native config screens
- NBT completion, recipe tools and inline item facts; no palette side panel or storage tracker
- Music player
- Games (like uh snake and the dino from chrome)

## FAQ
Q: It's ugli, how fix.

A: Change themes


Q: What's the purpose of this, I can just use other mods and the chat for commands?

A: idk


## Build and install

This branch is the direct **NeoForge 1.21.1** port, version **1.0.2**. Requires **Java 21**, **NeoForge 21.1.265+ within 21.1**, and **Kotlin for Forge 5.12.0+ within 5.x**. Install Kotlin for Forge separately; Fabric API/Fabric Language Kotlin are not used. Client-only; no server installation required. Creative/server permission requirements remain unchanged.

```sh
scripts/dev build
scripts/dev runClient
```

Gradle/compiler use JDK 25; game bytecode targets Java 21. If Java 21 discovery fails, pass `-Dorg.gradle.java.installations.paths=/path/to/jdk21`.

Production artifact: `build/libs/spotlight-neoforge-mc1.21.1-1.0.2.jar`. Development runtime: `run/neoforge-1.21.1/`.

Feature code is retained from patched Fabric 1.21.1, including live text preview fixes. NeoForge supplies lifecycle/keybind/render/client-command events. All 41 assertion entrypoints run through native NeoForge loader-aware test harness; release checks reject Fabric references/test classes and verify metadata/mixins. Loader-backed assertions pass; graphical client startup, exhaustive UI/audio and mod-interoperability parity remain to be manually verified for this release.

Installed command extensions use Java ServiceLoader: implement `dev.spotlight.platform.SpotlightExtension` and list provider class in `META-INF/services/dev.spotlight.platform.SpotlightExtension`. Providers load after built-ins on client setup thread. Fabric entrypoint metadata is not used. Optional map adapters remain separate; no built-in Xaero/JourneyMap adapter is claimed.

Local recipe index reads vanilla plus discovered NeoForge mod-file resources. Conditional/dynamic recipes needing reload context are reported as unsupported rather than evaluated with invented context. This branch does not target Forge 1.20.1; that port is separate.


## Head database

Opening `heads` loads Minecraft Heads V1 categories automatically, with separate `server_heads` for client-visible heads. Category caches remain available offline. Online refresh contacts minecraft-heads.com; downloads run sequentially with a one-second request interval and a 60-second refresh cooldown. Search runs in the background; previews load lazily within a per-tick budget.

```sh
scripts/dev build check --offline
scripts/dev remoteHeadsCheck --offline -PliveHeads
```

`-PliveHeads` explicitly enables network validation; omit for offline checks.

## Attributions

[![Minecraft Heads](docs/images/minecraft-heads.webp)](https://minecraft-heads.com/)

When I don't have inspiration, I just steal from other mods.

thank you
- [ClientCommands](https://github.com/Earthcomputer/clientcommands)
- [FZMM](https://github.com/Zailer43/FZMM-Mod)
