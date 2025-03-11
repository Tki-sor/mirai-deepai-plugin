package com.tkisor.mirai.deepai

import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.event.*
import kotlin.properties.*
import kotlin.reflect.*

@PublishedApi
internal object DeepAIPermissions : ReadOnlyProperty<SimpleListenerHost, Permission> {
    private val records: MutableMap<String, Permission> = HashMap()

    @Synchronized
    override fun getValue(thisRef: SimpleListenerHost, property: KProperty<*>): Permission {
        return records[property.name] ?: kotlin.run {
            val permission = PermissionService.INSTANCE.register(
                id = DeepAIPlugin.permissionId(property.name),
                description = "触发 ${property}_prefix",
                parent = DeepAIPlugin.parentPermission
            )

            records[property.name] = permission

            DeepAIPlugin.logger.info("${property.name}_prefix 权限 ${permission.id}")

            permission
        }
    }
}