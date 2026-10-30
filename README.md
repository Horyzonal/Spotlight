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

Production artifact: `build/libs/spotlight-mc1.21.4-0.1.0.jar`.

Development client uses `run/1.21.4/` to avoid opening newer worlds/configuration in an older game. Do not copy newer-version worlds into this directory without a backup. Port targets Minecraft 1.21.4 with Fabric Loader 0.20.1 and Fabric API 0.119.4+1.21.4. Build uses JDK 25; Java and Kotlin target Java 21 bytecode. Checks and client startup were exercised on Java 21. Other Minecraft versions require separate builds.

## Attributions
When I don't have inspiration, I just steal from other mods.

thank you
- [ClientCommands](https://github.com/Earthcomputer/clientcommands)
- [FZMM](https://github.com/Zailer43/FZMM-Mod)
