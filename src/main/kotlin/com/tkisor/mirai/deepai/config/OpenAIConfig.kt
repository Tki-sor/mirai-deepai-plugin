package com.tkisor.mirai.deepai.config

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*


internal object OpenAIConfig : ReadOnlyPluginConfig(saveName = "openai") {
    @ValueName("proxy")
    @ValueDescription("配置时请注意单引号")
    val proxy: String by value("")

    @ValueName("timeout")
    @ValueDescription("API 超时时间, 如果出现 TimeoutException 时, 请尝试调大")
    val timeout: Long by value(30_000L)

    @ValueName("api")
    @ValueDescription("API 地址")
    val api: String by value("https://api.deepseek.com/v1")

    @ValueName("token")
    @ValueDescription("OPENAI_TOKEN")
    val token: String by value(System.getenv("OPENAI_TOKEN").orEmpty())

    @ValueName("error_reply")
    @ValueDescription("发生错误时回复用户")
    val reply: Boolean by value(true)

    @ValueName("end_reply")
    @ValueDescription("停止聊天时回复用户")
    val bye: Boolean by value(false)

    @ValueName("chat_prefix")
    @ValueDescription("聊天模型触发前缀")
    val chat: String by value("chat")

    @ValueName("reload_prefix")
    @ValueDescription("重载配置触发前缀")
    val reload: String by value("openai-reload")

    @ValueName("chat_by_at")
    @ValueDescription("聊天模型触发于@")
    val chatByAt: Boolean by value(false)

    @ValueName("prompts_prefix")
    @ValueDescription("展示 prompts 列表触发前缀")
    val prompts: String by value("prompts")

    @ValueName("stop")
    @ValueDescription("停止聊天或问答")
    val stop: String by value("stop")

    @ValueName("keep_prefix_check")
    @ValueDescription("保持前缀检查")
    val prefix: Boolean by value(false)

    @ValueName("chat_limit")
    @ValueDescription("聊天服务个数限制")
    val limit: Int by value(10)

    @ValueName("has_permission")
    @ValueDescription("权限检查")
    val permission: Boolean by value(false)

    @ValueName("bind_set_prefix")
    @ValueDescription("绑定设置触发前缀")
    val bind: String by value("bind")

    @ValueName("bind_model_set_prefix")
    @ValueDescription("绑定模型设置触发前缀")
    val bind_model: String by value("bind_model")

    fun proxy(): Proxy {
        if (proxy.isEmpty()) {
            return Proxy.NO_PROXY
        }

        var protocol = "http"
        var hostAndPort: String = proxy

        if (proxy.contains("://")) {
            val split = proxy.split("://".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            protocol = split[0]
            hostAndPort = split[1]
        }

        val parts = hostAndPort.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        require(parts.size == 2) { "Invalid proxy format: $proxy" }

        val host = parts[0]
        val port = parts[1].toInt()

        val type: Proxy.Type = when (protocol.lowercase(Locale.getDefault())) {
            "http" -> Proxy.Type.HTTP
            "socks", "socks4", "socks5" -> Proxy.Type.SOCKS
            else -> throw IllegalArgumentException("Unsupported proxy protocol: $protocol")
        }
        return Proxy(type, InetSocketAddress(host, port))
    }
}