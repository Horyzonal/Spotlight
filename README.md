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

Requires Minecraft Java **1.20.1**, **Java 17**, **Forge 47.4.28+ within 47.x** (or legacy **NeoForge 47.1.106**) and **Kotlin for Forge 4.12.0+ within 4.x**. Client-only; install Kotlin for Forge separately. Fabric dependencies are not used. Exact dependency versions are pinned in `gradle.properties`.

```sh
scripts/dev build
```

Production artifact: `build/libs/spotlight-forge-mc1.20.1-1.0.2.jar`. Development runtime: `run/forge-1.20.1/`.


```sh
scripts/dev build check --offline
scripts/dev remoteHeadsCheck --offline -PliveHeads
```

Legacy NeoForge: `scripts/dev build check --offline -Ploader=neoforge`; artifact: `build/neoforge/libs/spotlight-forge-mc1.20.1-1.0.2.jar`; runtime: `run/neoforge-1.20.1/`.

## Attributions

[![Minecraft Heads](docs/images/minecraft-heads.webp)](https://minecraft-heads.com/)

When I don't have inspiration, I just steal from other mods.

thank you
- [ClientCommands](https://github.com/Earthcomputer/clientcommands)
- [FZMM](https://github.com/Zailer43/FZMM-Mod)
- [Mod Menu](https://modrinth.com/mod/modmenu)
- [Hey Wiki](https://modrinth.com/mod/hey-wiki)
- [NBT Editor](https://modrinth.com/mod/nbt-editor)

For ideas, concepts and inspiration.
