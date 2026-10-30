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


```sh
scripts/dev clean build
```

Production artifact: `build/libs/spotlight-mc1.20.1-0.1.0.jar`.

Targets **Minecraft 1.20.1 / Java 17**, Fabric Loader, Fabric API **0.92.11+1.20.1**, and Fabric Language Kotlin. Gradle/build tools use JDK 25; mod bytecode and verification programs target Java 17. Development runtime is isolated under `run/1.20.1/`; never downgrade newer worlds without backups.

This port uses legacy item NBT for names, lore, banners, shields and Unbreakable while preserving unrelated data. Local crafting index reads shipped vanilla/mod recipes; custom serializers requiring newer/native reload contexts remain explicitly unsupported instead of being guessed. Integrated-server recipe index remains available separately. Build checks and bounded Java 17 startup passed; full UI/multiplayer acceptance remains manual.

## Attributions
When I don't have inspiration, I just steal from other mods.

thank you
- [ClientCommands](https://github.com/Earthcomputer/clientcommands)
- [FZMM](https://github.com/Zailer43/FZMM-Mod)
