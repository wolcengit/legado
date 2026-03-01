package io.legado.app.lib.cronet

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import io.legado.app.BuildConfig
import io.legado.app.constant.AppLog
import io.legado.app.help.http.Cronet
import io.legado.app.utils.DebugLog
import org.chromium.net.CronetEngine
import splitties.init.appCtx

@Suppress("ConstPropertyName")
@Keep
object CronetLoader : CronetEngine.Builder.LibraryLoader(), Cronet.LoaderInterface {

    private const val soVersion = BuildConfig.Cronet_Version
    private const val soName = "libcronet.$soVersion.so"
    private var cpuAbi: String? = null

    @Volatile
    private var cacheInstall = false

    /**
     * 是否通过 GMS Cronet Provider 回退加载
     * 当预置 SO 加载失败时，尝试使用平台提供的 Cronet 实现
     */
    @Volatile
    var isGmsProviderFallback = false
        private set

    /**
     * 判断Cronet是否安装完成
     *
     * 加载策略：
     * 1. 优先通过 System.loadLibrary 加载预置 SO
     * 2. 回退到 GMS Cronet Provider（平台实现）
     * 3. 所有方式均失败时返回 false，由调用方回退到纯 OkHttp
     */
    override fun install(): Boolean {
        synchronized(this) {
            if (cacheInstall) {
                return true
            }
        }
        // 1. 尝试通过 System.loadLibrary 加载预置 SO
        try {
            System.loadLibrary("cronet.$soVersion")
            DebugLog.d(javaClass.simpleName, "install: 预置SO加载成功")
            synchronized(this) {
                cacheInstall = true
                isGmsProviderFallback = false
            }
            return true
        } catch (e: Throwable) {
            DebugLog.d(javaClass.simpleName, "install: 预置SO加载失败: ${e.message}")
        }

        // 2. 尝试使用 GMS Cronet Provider（平台实现）
        try {
            Class.forName("org.chromium.net.impl.JavaCronetProvider")
            DebugLog.d(javaClass.simpleName, "install: 回退到 GMS Cronet Provider")
            synchronized(this) {
                cacheInstall = true
                isGmsProviderFallback = true
            }
            return true
        } catch (e: Throwable) {
            DebugLog.d(javaClass.simpleName, "install: GMS Cronet Provider 不可用: ${e.message}")
        }

        // 3. 所有方式均失败，记录日志，由调用方回退到纯 OkHttp
        AppLog.put("Cronet 加载失败：预置SO和GMS Provider均不可用，回退到纯OkHttp")
        synchronized(this) {
            cacheInstall = false
            isGmsProviderFallback = false
        }
        return false
    }

    /**
     * 预加载Cronet（远程下载逻辑已移除，仅使用预置SO）
     */
    override fun preDownload() {
        DebugLog.d(javaClass.simpleName, "preDownload: 使用预置SO库，跳过远程下载")
    }

    override fun loadLibrary(libName: String) {
        DebugLog.d(javaClass.simpleName, "libName:$libName")
        val start = System.currentTimeMillis()
        try {
            System.loadLibrary(libName)
            DebugLog.d(javaClass.simpleName, "load from system")
        } catch (e: Throwable) {
            DebugLog.d(javaClass.simpleName, "loadLibrary failed: ${e.message}")
            throw e
        } finally {
            DebugLog.d(javaClass.simpleName, "time:" + (System.currentTimeMillis() - start))
        }
    }

    private fun getCpuAbi(context: Context): String? {
        if (cpuAbi != null) {
            return cpuAbi
        }
        // 使用公开 API 获取 CPU ABI，替代反射访问 ApplicationInfo.primaryCpuAbi
        cpuAbi = Build.SUPPORTED_ABIS[0]
        return cpuAbi
    }

}
