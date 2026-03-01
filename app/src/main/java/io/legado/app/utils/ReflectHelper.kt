@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint

/**
 * 统一反射工具类，集中管理所有反射调用。
 * 避免在业务代码中散布反射调用，便于审计和追踪。
 */
@Suppress("DiscouragedPrivateApi")
object ReflectHelper {

    /**
     * 安全地获取字段值，失败时返回 null
     */
    @SuppressLint("DiscouragedPrivateApi")
    inline fun <reified T> getFieldValue(
        target: Any, fieldName: String
    ): T? = runCatching {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
        }.get(target) as? T
    }.getOrNull()

    /**
     * 安全地从指定类获取字段值（用于需要从父类获取字段的场景），失败时返回 null
     */
    @SuppressLint("DiscouragedPrivateApi")
    inline fun <reified T> getFieldValue(
        target: Any, clazz: Class<*>, fieldName: String
    ): T? = runCatching {
        clazz.getDeclaredField(fieldName).apply {
            isAccessible = true
        }.get(target) as? T
    }.getOrNull()

    /**
     * 安全地设置字段值，失败时返回 false
     */
    @SuppressLint("DiscouragedPrivateApi")
    fun setFieldValue(
        target: Any, fieldName: String, value: Any?
    ): Boolean = runCatching {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
        }.set(target, value)
    }.isSuccess

    /**
     * 安全地调用方法
     */
    @SuppressLint("DiscouragedPrivateApi")
    fun invokeMethod(
        target: Any, methodName: String,
        paramTypes: Array<Class<*>> = emptyArray(),
        args: Array<Any?> = emptyArray()
    ): Any? = runCatching {
        target.javaClass.getDeclaredMethod(methodName, *paramTypes).apply {
            isAccessible = true
        }.invoke(target, *args)
    }.getOrNull()

}
