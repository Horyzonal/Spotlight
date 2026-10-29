package dev.spotlight.commands

import dev.spotlight.platform.ServerPlugins
import dev.spotlight.platform.worldUnavailable

internal fun registerPlugins() {
    registerCommand("server_plugins", "Get server plugins", "Infer possible plugin namespaces from visible commands and at most one server completion request (3-second timeout); not a verified plugin list", unavailable = ::worldUnavailable) {
        ServerPlugins.query()
    }
}
