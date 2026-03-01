/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.script.rhino

import android.os.Build
import android.util.Log
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Member
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.Collections

/**
 * This class prevents script access to certain sensitive classes.
 * Note that this class checks over and above SecurityManager. i.e., although
 * a SecurityManager would pass, class shutter may still prevent access.
 *
 * @author A. Sundararajan
 * @since 1.6
 */
object RhinoClassShutter : ClassShutter {

    private const val TAG = "RhinoClassShutter"

    /**
     * 白名单：仅包含 JS 书源实际需要的 Java 类前缀。
     * 不在白名单中的类默认被拒绝访问。
     */
    private val allowedPrefixesMatcher by lazy {
        ClassNameMatcher(
            listOf(
                // Java 基本类型包装类和核心类
                "java.lang.String",
                "java.lang.Integer", "java.lang.Long", "java.lang.Double",
                "java.lang.Boolean", "java.lang.Float", "java.lang.Byte",
                "java.lang.Short", "java.lang.Character",
                "java.lang.Math",
                "java.lang.StringBuilder", "java.lang.Number",
                "java.lang.System",
                // Java 集合类
                "java.util.ArrayList", "java.util.HashMap", "java.util.LinkedHashMap",
                "java.util.LinkedList", "java.util.HashSet", "java.util.TreeMap",
                "java.util.Arrays", "java.util.Collections", "java.util.regex",
                // Java IO（仅流相关，不含 File）
                "java.io.PrintStream",
                // Java 文本格式化
                "java.text.SimpleDateFormat", "java.text.DecimalFormat",
                // Java 网络工具（仅编码/解码）
                "java.net.URLEncoder", "java.net.URLDecoder",
                // Jsoup HTML 解析
                "org.jsoup",
                // Hutool 工具类（编码、加密相关）
                "cn.hutool.core.codec", "cn.hutool.core.util.HexUtil",
                "cn.hutool.core.util.CharsetUtil", "cn.hutool.core.util.StrUtil",
                "cn.hutool.crypto",
                // legado 应用（数据实体、扩展接口等 JS 书源需要访问的类）
                "io.legado.app",
            )
        )
    }

    /**
     * 黑名单：包含危险类前缀，优先级高于白名单。
     * 即使某个类匹配白名单，如果同时匹配黑名单也会被拒绝。
     */
    private val blockedPrefixesMatcher by lazy {
        ClassNameMatcher(
            listOf(
                "java.lang.Runtime", "java.lang.ProcessBuilder",
                "java.lang.reflect", "java.lang.invoke",
                "java.io.File", "java.net.URLClassLoader",
                "dalvik.system", "com.script", "org.mozilla",
                // legado 内部敏感类（数据库、上下文等）
                "io.legado.app.data.AppDatabase",
                "io.legado.app.data.dao",
            )
        )
    }

    private val systemClassProtectedName by lazy {
        Collections.unmodifiableSet(hashSetOf("load", "loadLibrary", "exit"))
    }

    private val protectedClasses by lazy {
        arrayOf(
            ClassLoader::class.java,
            Class::class.java,
            Member::class.java,
            Context::class.java,
            ObjectInputStream::class.java,
            ObjectOutputStream::class.java,
            okio.FileSystem::class.java,
            okio.FileHandle::class.java,
            okio.Path::class.java,
            android.content.Context::class.java,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(FileSystem::class.java, Path::class.java)
        } else {
            emptyArray()
        }
    }

    fun visibleToScripts(obj: Any): Boolean {
        when (obj) {
            is ClassLoader,
            is Class<*>,
            is Member,
            is Context,
            is ObjectInputStream,
            is ObjectOutputStream,
            is okio.FileSystem,
            is okio.FileHandle,
            is okio.Path,
            is android.content.Context -> return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (obj) {
                is FileSystem,
                is Path -> return false
            }
        }
        return visibleToScripts(obj.javaClass.name)
    }

    fun visibleToScripts(clazz: Class<*>): Boolean {
        protectedClasses.forEach {
            if (it.isAssignableFrom(clazz)) {
                return false
            }
        }
        return true
    }

    fun wrapJavaClass(scope: Scriptable, javaClass: Class<*>): Scriptable {
        return when (javaClass) {
            System::class.java -> {
                ProtectedNativeJavaClass(scope, javaClass, systemClassProtectedName)
            }

            else -> ProtectedNativeJavaClass(scope, javaClass)
        }
    }

    /**
     * 白名单 + 黑名单双重检查：
     * 1. 先检查黑名单（优先级最高），匹配则拒绝
     * 2. 再检查白名单，匹配则允许
     * 3. 不在白名单中的类默认拒绝
     *
     * 当访问被拒绝时，记录警告日志并返回 false。
     * Rhino 引擎在收到 false 后会自动抛出安全相关异常。
     */
    override fun visibleToScripts(fullClassName: String): Boolean {
        if (blockedPrefixesMatcher.match(fullClassName)) {
            logWarning("Security: script access denied for blocked class: $fullClassName")
            return false
        }
        if (allowedPrefixesMatcher.match(fullClassName)) {
            return true
        }
        logWarning("Security: script access denied for non-whitelisted class: $fullClassName")
        return false
    }

    /**
     * 安全地记录警告日志，在 JVM 单元测试环境中 android.util.Log 不可用时
     * 回退到 stderr 输出，避免测试崩溃。
     */
    private fun logWarning(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Exception) {
            System.err.println("W/$TAG: $message")
        }
    }

}