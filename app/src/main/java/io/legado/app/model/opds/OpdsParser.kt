package io.legado.app.model.opds

import io.legado.app.data.entities.OpdsEntry
import io.legado.app.data.entities.OpdsFeed
import io.legado.app.data.entities.OpdsLink
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.shared.opds.Feed
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder

/**
 * OPDS Feed 解析适配器。
 * 将 Readium OPDS1Parser 的解析结果转换为应用内部数据模型。
 */
object OpdsParser {

    /** Readium Feed type 常量：Navigation = 1, Acquisition = 2 */
    private const val FEED_TYPE_NAVIGATION = 1

    /**
     * 解析 OPDS Feed XML 内容。
     *
     * @param content 原始 XML 响应内容
     * @param url Feed 的 URL
     * @return 解析后的 OpdsFeed
     * @throws OpdsParseException 解析失败时抛出，包含描述性错误信息
     */
    fun parseFeed(content: String, url: String): OpdsFeed {
        val feedUrl = Url(url)
            ?: throw OpdsParseException("无效的 Feed URL: $url")

        val parseData = try {
            OPDS1Parser.parse(content.toByteArray(Charsets.UTF_8), feedUrl)
        } catch (e: Throwable) {
            throw OpdsParseException("OPDS Feed 解析失败: ${e.message}", e)
        }

        val feed = parseData.feed
            ?: throw OpdsParseException("OPDS Feed 解析结果为空")

        return convertFeed(feed, parseData.publication, url)
    }

    /**
     * 解析 OpenSearch Descriptor XML，提取搜索 URL 模板。
     *
     * @param content OpenSearch XML 内容
     * @return 搜索 URL 模板字符串，解析失败返回 null
     */
    fun parseOpenSearchDescriptor(content: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                    parser.name.equals("Url", ignoreCase = true)
                ) {
                    val template = parser.getAttributeValue(null, "template")
                    if (!template.isNullOrBlank()) {
                        return template
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 验证 URL 是否为合法的 OPDS 目录 URL。
     * URL 必须以 http:// 或 https:// 开头且包含有效（非空）主机名。
     *
     * @param url 待验证的 URL 字符串
     * @return true 如果 URL 合法，false 否则
     */
    fun isValidOpdsUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 构造 OpenSearch 搜索 URL。
     * 将模板中的 `{searchTerms}` 替换为 URL 编码后的搜索关键词。
     *
     * @param template OpenSearch URL 模板，包含 `{searchTerms}` 占位符
     * @param query 搜索关键词
     * @return 替换后的搜索 URL
     */
    fun buildSearchUrl(template: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return template.replace("{searchTerms}", encoded)
    }

    /**
     * 将 Readium Feed + Publication 转换为内部 OpdsFeed 模型。
     */
    private fun convertFeed(feed: Feed, publication: Publication?, baseUrl: String): OpdsFeed {
        val isNavigation = feed.type == FEED_TYPE_NAVIGATION

        // 从 navigation links 构建导航条目
        val navigationEntries = feed.navigation.map { link ->
            convertNavigationLink(link, baseUrl)
        }

        // 从 publications 构建书籍条目
        val publicationEntries = feed.publications.map { pub ->
            convertPublication(pub, baseUrl)
        }

        val entries = navigationEntries + publicationEntries

        // 转换 Feed 级别链接
        val feedLinks = feed.links.map { convertLink(it, baseUrl) }

        // 提取搜索 URL
        val searchUrl = feed.links
            .firstOrNull { link ->
                link.rels.contains("search") &&
                    link.mediaType?.toString()?.contains("opensearchdescription") == true
            }?.let { resolveHref(it, baseUrl) }

        return OpdsFeed(
            id = feed.href.toString(),
            title = feed.title,
            updated = feed.metadata.modified?.toString(),
            author = null,
            entries = entries,
            links = feedLinks,
            isNavigation = isNavigation,
            searchUrl = searchUrl
        )
    }

    /**
     * 将 Readium navigation Link 转换为 OpdsEntry。
     */
    private fun convertNavigationLink(link: Link, baseUrl: String): OpdsEntry {
        val href = resolveHref(link, baseUrl)
        return OpdsEntry(
            id = href,
            title = link.title ?: "",
            links = listOf(
                OpdsLink(
                    href = href,
                    type = link.mediaType?.toString(),
                    rel = link.rels.firstOrNull(),
                    title = link.title
                )
            )
        )
    }

    /**
     * 将 Readium Publication 转换为 OpdsEntry。
     */
    private fun convertPublication(pub: Publication, baseUrl: String): OpdsEntry {
        val metadata = pub.metadata
        val allLinks = pub.links.map { convertLink(it, baseUrl) }

        val acquisitionLinks = allLinks.filter { it.isAcquisition }

        // 提取封面图片 URL
        val coverUrl = pub.links
            .firstOrNull { link ->
                link.rels.any { it == "http://opds-spec.org/image" || it == "http://opds-spec.org/image/thumbnail" }
            }?.let { resolveHref(it, baseUrl) }
            ?: pub.links
                .firstOrNull { link ->
                    link.mediaType?.toString()?.startsWith("image/") == true
                }?.let { resolveHref(it, baseUrl) }

        return OpdsEntry(
            id = metadata.identifier ?: metadata.title ?: "",
            title = metadata.title ?: "",
            author = metadata.authors.joinToString(", ") { it.name },
            summary = metadata.description,
            updated = metadata.modified?.toString(),
            coverUrl = coverUrl,
            links = allLinks,
            acquisitionLinks = acquisitionLinks
        )
    }

    /**
     * 将 Readium Link 转换为内部 OpdsLink。
     */
    private fun convertLink(link: Link, baseUrl: String): OpdsLink {
        return OpdsLink(
            href = resolveHref(link, baseUrl),
            type = link.mediaType?.toString(),
            rel = link.rels.firstOrNull(),
            title = link.title
        )
    }

    /**
     * 解析 Link 的 href，相对 URL 基于 baseUrl 解析为绝对 URL。
     */
    private fun resolveHref(link: Link, baseUrl: String): String {
        val base = Url(baseUrl)
        return if (base != null) {
            link.url(base).toString()
        } else {
            link.href.toString()
        }
    }
}

/**
 * OPDS 解析异常，包含描述性错误信息。
 */
class OpdsParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
