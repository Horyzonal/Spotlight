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

Requires Minecraft Java **26.3**, **Java 25**, Fabric Loader, Fabric API and Fabric Language Kotlin. Exact dependency versions are pinned in `gradle.properties`.

```sh
scripts/dev clean build
```

Production artifact: `build/libs/spotlight-0.1.0.jar`. Install this JAR with required dependencies in your instance's `mods` directory. No server installation required. Some actions require Creative or server command permissions.

Build runs 31 verification programs and checks release contents. Verification sources live in `src/test`; production JAR excludes them, includes license notices, and uses reproducible archive ordering/timestamps. No risky bytecode shrinking or obfuscation. Runtime multiplayer/UI/audio acceptance remains separate from automated checks.

## Attributions
When I don't have inspiration, I just steal from other mods.

thank you
- [ClientCommands](https://github.com/Earthcomputer/clientcommands)
- [FZMM](https://github.com/Zailer43/FZMM-Mod)
