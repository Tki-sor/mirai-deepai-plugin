package com.tkisor.mirai.deepai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.core.Timeout
import com.openai.errors.BadRequestException
import com.openai.errors.NotFoundException
import com.openai.errors.OpenAIException
import com.openai.errors.UnauthorizedException
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatCompletionMessage
import com.tkisor.mirai.deepai.DeepAIPlugin.save
import com.tkisor.mirai.deepai.config.ChatConfig
import com.tkisor.mirai.deepai.config.ChatOption
import com.tkisor.mirai.deepai.config.OpenAIConfig
import com.tkisor.mirai.deepai.data.Prompts
import com.tkisor.mirai.deepai.data.UserBind
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.NormalMember
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.ExceptionInEventHandlerException
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.nextMessage
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.OverFileSizeMaxException
import net.mamoe.mirai.utils.info
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext

@PublishedApi
internal object DeepAIListener : SimpleListenerHost() {
    private val client: OpenAIClient = OpenAIOkHttpClient.builder()
        .apiKey(OpenAIConfig.token)
        .baseUrl(OpenAIConfig.api)
        .proxy(OpenAIConfig.proxy())
        .timeout(Duration.ofMillis(OpenAIConfig.timeout))
        .build()
    private val lock: MutableMap<Long, MessageEvent> = java.util.concurrent.ConcurrentHashMap()
    private val logger = MiraiLogger.Factory.create(this::class)
    internal val chat: Permission by DeepAIPermissions
    internal val reload: Permission by DeepAIPermissions
    internal val bind_group: Permission by DeepAIPermissions

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        when (exception) {
            is ExceptionInEventHandlerException -> {
                val event = exception.event as? MessageEvent ?: return
                lock.remove(event.sender.id, event)

                if (OpenAIConfig.reply) launch {
                    when (exception.cause) {
                        is SocketTimeoutException -> {
                            event.subject.sendMessage(event.message.quote() + "OpenAI API 超时 请重试")
                        }

                        is OverFileSizeMaxException -> {
                            event.subject.sendMessage(event.message.quote() + "OpenAI API 生成图片过大, 请重试")
                        }

                        is NotFoundException -> {
                            event.subject.sendMessage(
                                event.message.quote() + errorMsg(
                                    "未找到API，可能是你的API地址有误",
                                    exception
                                )
                            )
                        }

                        is UnauthorizedException -> {
                            event.subject.sendMessage(
                                event.message.quote() + errorMsg(
                                    "OpenAI API 未授权，可能是token或api错误",
                                    exception
                                )
                            )
                        }

                        is BadRequestException -> {
                            event.subject.sendMessage(
                                event.message.quote() + errorMsg(
                                    "API请求异常，也许是你的配置或网络有些问题",
                                    exception
                                )
                            )
                        }

                        is OpenAIException -> {
                            event.subject.sendMessage(
                                event.message.quote() + errorMsg(
                                    "发生错误！OpenAIException：可能是你的聊天有些问题，一般与插件作者无关",
                                    exception
                                )
                            )
                        }

                        else -> {
                            event.subject.sendMessage(event.message.quote() + errorMsg("未统计到的错误！", exception))
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    fun errorMsg(msg: String, exception: Throwable): String {
        val cause = exception.cause

        val errorMessage = buildString {
            appendLine(msg)
            appendLine("可以选择将该错误信息反馈给作者，以帮助完善插件")
            cause?.let { appendLine("类型：${cause::class.simpleName}}") }
            appendLine("信息：${exception.message}")
            cause?.let { appendLine("原因：${cause.message ?: "无"}") }
        }
        return errorMessage
    }

    @EventHandler
    suspend fun handle(event: MessageEvent) {
        val content = event.message.contentToString()

        when {
            content.startsWith(OpenAIConfig.chat) && (OpenAIConfig.permission.not()
                    || event.toCommandSender().hasPermission(chat))
            -> chat(event)

            content.startsWith(OpenAIConfig.reload) && (OpenAIConfig.permission.not()
                    || event.toCommandSender().hasPermission(reload))
            -> with(DeepAIPlugin) {
                config.forEach { config ->
                    config.reload()
                }
                event.subject.sendMessage("OPENAI 配置已重载")
            }

            event.message.findIsInstance<At>()?.target == event.bot.id && OpenAIConfig.chatByAt -> chat(event)
            else -> return
        }
    }


    private suspend fun chat(event: MessageEvent) {
        val id = event.sender.id

        if (lock.size >= OpenAIConfig.limit) {
            launch {
                event.subject.sendMessage("聊天服务已开启过多，请稍后重试".toPlainText())
            }
            return
        }

        val config = findConfig(event) ?: return

        val system = event.message
            .findIsInstance<PlainText>()?.content.orEmpty()
            .removePrefix(OpenAIConfig.chat)
            .replace("""#(<.+?>|\S+)""".toRegex()) { match ->
                val (path) = match.destructured
                try {
                    Prompts.prompt(path = path.removeSurrounding("<", ">"))
                } catch (exception: FileNotFoundException) {
                    logger.warning("文件不存在")
                    launch {
                        event.subject.sendMessage(event.message.quote() + "文件不存在")
                    }
                    match.value
                }
            }
            .replace("""[~.]\s+""".toRegex()) { _ -> Prompts.prompt(event.sender.id, event.subject.id) }
            .ifBlank { Prompts.prompt(event.sender.id, event.subject.id) }

        lock[event.sender.id] = event


        val buffer: MutableList<ChatCompletionMessage> = mutableListOf()

        val message = send(event, system, "你好啊", buffer)
        event.subject.sendMessage(event.message.quote() + message)

        launch {
            while (isActive) {
                val next = event.nextMessage(config.timeout, EventPriority.HIGH, intercept = true) {
                    val text = it.message.contentToString()
                    when {
                        text == OpenAIConfig.stop -> true
                        OpenAIConfig.prefix -> text.startsWith(OpenAIConfig.chat) ||
                                (it.message.findIsInstance<At>()?.target == event.bot.id && OpenAIConfig.chatByAt)

                        else -> true
                    }
                }
                val content = if (OpenAIConfig.prefix) {
                    next.contentToString().removePrefix(OpenAIConfig.chat)
                } else {
                    next.contentToString()
                }
                if (content == OpenAIConfig.stop) break


                val reply = send(event = event, system, content, buffer = buffer)
                launch {
                    event.subject.sendMessage(next.quote() + reply)
                }
                buffer.add(
                    ChatCompletionMessage.builder()
                        .role(JsonValue.from("user"))
                        .refusal("")
                        .content(content)
                        .build()
                )
                buffer.add(
                    ChatCompletionMessage.builder()
                        .role(JsonValue.from("assistant"))
                        .refusal("")
                        .content(reply)
                        .build()
                )
            }
        }.invokeOnCompletion { cause ->
            lock.remove(event.sender.id, event)
            when (cause) {
                null -> Unit
                is TimeoutCancellationException -> logger.info { "聊天已终止 ${event.sender}" }
                else -> handleException(coroutineContext, ExceptionInEventHandlerException(event, cause = cause))
            }
            if (OpenAIConfig.bye) {
                launch {
                    event.subject.sendMessage(event.message.quote() + "聊天已终止")
                }
            }
        }
    }

    private suspend fun send(
        event: MessageEvent,
        system: String,
        send: String,
        buffer: MutableList<ChatCompletionMessage>
    ): String {
        val config = findConfig(event)!!

        val createParamsBuilder = ChatCompletionCreateParams.builder()
            .model(config.model)
            .maxCompletionTokens(config.maxTokens.toLong())
            .addSystemMessage(system)

        buffer.forEach { createParamsBuilder.addMessage(it) }
        val stringBuilder = StringBuilder()

        if (config.stream) {
            val response = client.chat().completions()
                .createStreaming(createParamsBuilder.addUserMessage(send).build())


            var lastEventTime: Long? = null
            try {
                val iterator = response.stream().iterator()
                while (iterator.hasNext()) {
                    try {
                        // 监控每个事件的等待时间（包括获取事件的时间）
                        withTimeout(config.timeout) {
                            val e = iterator.next() // 可能被阻塞
                            val currentTime = System.currentTimeMillis()

                            // 检查事件间隔是否超过阈值（忽略第一个事件）
                            if (lastEventTime != null) {
                                val elapsedTime = currentTime - lastEventTime!!
                                if (elapsedTime > config.timeout) {
                                    throw TimeoutException("Event interval too long: $elapsedTime ms")
                                }
                            }

                            lastEventTime = currentTime // 更新时间戳
                            // 处理事件
                            e.choices().forEach { q ->
                                q.delta().content().orElse(null)?.let { t ->
                                    stringBuilder.append(t)
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        // 获取事件超时（等待下一个事件的时间过长）
                        throw TimeoutException("Timeout waiting for event $e")
                    } catch (e: Exception) {
                        // 其他异常继续抛出
                        throw e
                    }
                }
            } catch (e: TimeoutException) {
                // 超时处理
                response.close()
                throw RuntimeException("Stream processing timed out", e)
            } catch (e: Exception) {
                // 其他异常处理
                response.close()
                throw e
            } finally {
                // 确保关闭流
                response.close()
            }


        } else {
            withTimeout(config.timeout) {
                val response = client.chat().completions()
                    .create(createParamsBuilder.addUserMessage(send).build())
                response.choices().forEach { t ->
                    t.message().content().let {
                        stringBuilder.append(it)
                    }
                }
            }
        }

        val msg = stringBuilder.toString()
        return msg
    }

    @EventHandler
    fun MessageEvent.bind() {
        if (OpenAIConfig.permission && toCommandSender().hasPermission(chat).not()) return
        if (this.subject is Group && toCommandSender().hasPermission(bind_group).not()) return

        val content = message.contentToString()
        if (content.startsWith(OpenAIConfig.bind+" ").not()) return
        val path = content
            .removePrefix(OpenAIConfig.bind+" ")
            .trim()
        val target = when {
            (sender as? NormalMember)?.isOperator() ?: false -> (sender as NormalMember).group
            else -> sender
        }
        val prompt = try {
            if (path == "default") {
                Prompts.unbind(id = target.id)
            } else {
                Prompts.bind(id = target.id, path = path)
            }
        } catch (_: FileNotFoundException) {
            launch {
                subject.sendMessage("文件不存在")
            }
            return
        }
        launch {
            subject.sendMessage(buildMessageChain {
                if (target is Group) {
                    appendLine("将为群绑定 $path")
                } else {
                    appendLine("将为你绑定 $path")
                }
                appendLine()
                appendLine(prompt)
            })
        }
    }

    @EventHandler
    fun MessageEvent.bind_model() {
        if (OpenAIConfig.permission && toCommandSender().hasPermission(chat).not()) return
        if (this.subject is Group && toCommandSender().hasPermission(bind_group).not()) return

        val content = message.contentToString()
        if (content.startsWith(OpenAIConfig.bind_model+" ").not()) return
        val model = content
            .removePrefix(OpenAIConfig.bind_model+" ")
            .trim()
        val target = when {
            (sender as? NormalMember)?.isOperator() ?: false -> (sender as NormalMember).group
            else -> sender
        }
        when {
            model == "default" -> {
                val b_model = ChatConfig.chatOption.firstOrNull()?.model
                if (b_model == null) {
                    launch {
                        subject.sendMessage(message.quote() + "无任何可用模型，请联系机器人管理员")
                    }
                } else {
                    UserBind.bind(target.id, b_model)
                }
            }
            else -> {
                UserBind.bind(target.id, model)
            }
        }

        launch {
            subject.sendMessage(buildMessageChain {
                if (target is Group) {
                    append("将为群绑定 $model")
                } else {
                    append("将为你绑定 $model")
                }
            })
        }
    }

    private suspend fun findConfig(event: MessageEvent): ChatOption? {
        var model = UserBind.bind_model[event.sender.id]
        if (model == null) {
            event.subject.sendMessage(event.message.quote() + "绑定的模型为空，将尝试使用默认值")
            model = ChatConfig.chatOption.firstOrNull()?.model
            if (model == null) {
                event.subject.sendMessage(event.message.quote() + "无任何可用模型，请联系机器人管理员")
            } else {
                UserBind.bind(event.sender.id, model)
                UserBind.save()
            }
        }

        return model?.let { ChatConfig.findChatOption(it) }
    }
}