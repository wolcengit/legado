package io.legado.app.api.controller

import io.legado.app.data.entities.OpdsFeed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.opds.OpdsParseException
import io.legado.app.model.opds.OpdsParser
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * OPDS 业务控制器。
 * 协调网络请求与 Feed 解析，支持 HTTP Basic 认证。
 */
object OpdsController {

    /**
     * 获取并解析 OPDS Feed。
     *
     * @param url Feed URL
     * @param username HTTP Basic 认证用户名（可选）
     * @param password HTTP Basic 认证密码（可选）
     * @param client 用于发起请求的 OkHttpClient，默认使用全局 okHttpClient
     * @return 解析后的 OpdsFeed 数据模型
     * @throws IOException 网络请求失败时抛出
     * @throws OpdsParseException 响应内容解析失败时抛出
     */
    suspend fun fetchFeed(
        url: String,
        username: String? = null,
        password: String? = null,
        client: OkHttpClient = okHttpClient
    ): OpdsFeed {
        val content = try {
            client.newCallResponseBody {
                url(url)
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    header("Authorization", Credentials.basic(username, password))
                }
            }.text()
        } catch (e: IOException) {
            throw IOException("OPDS Feed 网络请求失败: ${e.message}", e)
        }
        return try {
            OpdsParser.parseFeed(content, url)
        } catch (e: OpdsParseException) {
            throw e
        } catch (e: Exception) {
            throw OpdsParseException("OPDS Feed 解析失败: ${e.message}", e)
        }
    }

    /**
     * 执行 OPDS 搜索。
     * 根据 OpenSearch URL 模板和搜索关键词构造搜索 URL，请求并解析搜索结果。
     *
     * @param searchUrlTemplate OpenSearch URL 模板，包含 {searchTerms} 占位符
     * @param query 搜索关键词
     * @param username HTTP Basic 认证用户名（可选）
     * @param password HTTP Basic 认证密码（可选）
     * @param client 用于发起请求的 OkHttpClient，默认使用全局 okHttpClient
     * @return 搜索结果 OpdsFeed
     * @throws IOException 网络请求失败时抛出
     * @throws OpdsParseException 响应内容解析失败时抛出
     */
    suspend fun search(
        searchUrlTemplate: String,
        query: String,
        username: String? = null,
        password: String? = null,
        client: OkHttpClient = okHttpClient
    ): OpdsFeed {
        val searchUrl = OpdsParser.buildSearchUrl(searchUrlTemplate, query)
        return fetchFeed(searchUrl, username, password, client)
    }
}
