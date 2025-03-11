package com.tkisor.mirai.deepai

import com.tkisor.mirai.deepai.config.OpenAIConfig
import kotlinx.coroutines.cancel
import net.mamoe.mirai.console.data.PluginConfig
import net.mamoe.mirai.console.data.PluginData
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.ListenerHost
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.registerTo
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning

public object DeepAIPlugin: KotlinPlugin(
    JvmPluginDescription(
        id = "com.tkisor.deepai.plugin.mirai-deepai-plugin",
        name = "mirai-deepai-plugin",
        version = "1.0.0",
    ) {
        author("Tki_sor")
    }) {

    @PublishedApi
    internal val config: List<PluginConfig> by services()

    @PublishedApi
    internal val data: List<PluginData> by services()

    @PublishedApi
    internal val listeners: List<ListenerHost> by services()

    override fun onEnable() {
        for (config in config) config.reload()
        for (data in data) data.reload()


        for (listener in listeners) (listener as SimpleListenerHost).registerTo(globalEventChannel())

        if (OpenAIConfig.permission) {
            logger.info { "权限检查已开启" }
            DeepAIListener.bind_group
            DeepAIListener.chat
            DeepAIListener.reload
        }

        if (OpenAIConfig.chatByAt) {
            logger.warning { "@Bot 触发聊天已开启, 手机端引用消息会自带@，请注意不要误触" }
        }

    }

    override fun onDisable() {
        for (listener in listeners) (listener as SimpleListenerHost).cancel()
        for (config in config) config.save()
        for (data in data) data.save()
    }
}
