package com.tkisor.mirai.deepai.config

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object ChatConfig : ReadOnlyPluginConfig(saveName = "chat") {
    @ValueName("chat_option")
    @ValueDescription(
        """
        本选项具有以下配置：
        model：ai的对话模型
        stream：是否开启流式输出，此选项仅影响代码层面的解析方式，如果模型仅支持流式输出请开启
        timeout：超时时间，单位毫秒。流式输出时每次输出都会重置超时
        max_tokens：模型输出文本的最大长度
        temperature：越大模型樾严谨，所以用来聊天不要调整过大，写代码之类可以调高一点
        top_p：
        presence_penalty：
        frequency_penalty：
        可以支持多个模型，使用者默认可以通过chat_config来进行配置
    """
    )
    val chatOption: List<ChatOption> by value(listOf(ChatOption()))

    fun findChatOption(model: String): ChatOption? {
        // 转换为 Map（只需一次转换）
        val modelToOptionMap = chatOption.associateBy { it.model }

        // 查找时直接通过 Map 获取
        val foundOption = modelToOptionMap[model]
        return foundOption
    }
}