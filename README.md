# Spotlight

The minecraft equivilant of MacOS' spotlight, Windows' Powertools, Raycast, Alfred and Vicinae.

## How use
The default hotkey is `` ` ``. Type in your command to use.

Though most features can be used by the keyboard, there are a few commands for the mouse.

## Features
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
- Music player
- Games (like uh snake and the dino from chrome)

## FAQ
Q: It's ugli, how fix.

A: Change themes


Q: What's the purpose of this, I can just use other mods and the chat for commands?

A: idk


## Build and install

Forge/legacy NeoForge port for **Minecraft 1.20.1**, version **1.0.1**. Requires **Java 17**, **Kotlin for Forge 4.12.0**, and either **Forge 47.4.28** or **legacy NeoForge 47.1.106**. Install only one loader. Client-only; Fabric API and Fabric Language Kotlin are not used. NeoForge 1.21.x builds are not compatible with this version.

```sh
scripts/dev build
scripts/dev runClient
scripts/dev -Ploader=neoforge build
scripts/dev -Ploader=neoforge runClient
```

Shared production JAR: `build/libs/spotlight-forge-mc1.20.1-1.0.1.jar`. NeoForge build writes same artifact under `build/neoforge/libs/`; validated outputs are byte-identical. Both builds run all 40 assertion entrypoints through native loader setup, plus release/reobfuscation checks.

Gradle 8.14.3 requires supported build JDK 17–24; bytecode targets Java 17. Development instances are isolated under `run/forge-1.20.1` and `run/neoforge-1.20.1`.

Earlier 0.1.1 client startup smoke tests initialized rendering/audio with Kotlin for Forge. Version 1.0.1 checks use native loader/Mixin setup without starting Minecraft. Full in-world, audio/UI and multiplayer parity remains manual acceptance. On this Mac Forge's early splash initially failed primary-monitor detection; only disposable dev config disabled early window. Production mod does not change user display settings.

Extensions use `META-INF/services/dev.spotlight.platform.SpotlightExtension` providers, not Fabric entrypoints. Optional map adapters remain separate. Local recipe index reads vanilla and discovered mod-file packs; conditional resources requiring reload context are reported unsupported.

Version 1.0.1 includes local client chest, 41-slot kits, replace/delete/archive and live search, online heads, wiki search, enchantment editors, legacy NBT completion, all vanilla recipe workstations, persistent endless music, native installed-mod settings and inline item facts. No extra item panel or storage tracker. Opening online heads contacts Minecraft Heads automatically; wiki contacts remote services when requested; cached/offline data remain available.

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
