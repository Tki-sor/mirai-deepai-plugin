package com.tkisor.mirai.deepai.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public class ChatOption {
    @SerialName("model")
    public val model: String = "deepseek-chat"

    @SerialName("stream")
    public val stream: Boolean = false

    @SerialName("timeout")
    public val timeout: Long = 60_000L

    @SerialName("max_tokens")
    public val maxTokens: Int = 512

    @SerialName("temperature")
    public val temperature: Double = 0.78

    @SerialName("top_p")
    public val topP: Double = 1.0

    @SerialName("presence_penalty")
    public val presencePenalty: Double = 0.6

    @SerialName("frequency_penalty")
    public val frequencyPenalty: Double = 0.0
}