package taboolib.platform.util

import org.bukkit.plugin.Plugin

lateinit var bukkitPluginStub: Plugin
val bukkitPlugin: Plugin
    get() = bukkitPluginStub
