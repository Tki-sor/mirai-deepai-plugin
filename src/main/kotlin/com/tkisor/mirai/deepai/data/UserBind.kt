package com.tkisor.mirai.deepai.data

import com.tkisor.mirai.deepai.config.ChatConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value


internal object UserBind : AutoSavePluginData(saveName = "user_bind") {
    @ValueName("bind_model")
    public val bind_model: MutableMap<Long, String> by value()

    public fun bind(id: Long, b_model: String): String? {
        return ChatConfig.chatOption.find { it.model == b_model }
            ?.model?.also { bind_model[id] = it }
    }

    public fun unbind(id: Long): String? {
        return bind_model.remove(id)
    }
}