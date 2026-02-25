package io.legado.app.model.opds

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.OpdsEntry
import io.legado.app.data.entities.OpdsLink
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.localBook.LocalBook
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * OPDS 书籍下载器。
 * 从 OPDS 获取链接下载书籍文件并通过 LocalBook 导入到本地书架。
 */
object OpdsDownloader {

    /**
     * 下载书籍并导入到本地书架。
     *
     * @param entry OPDS 书籍条目，提供元数据（书名、作者、简介、封面）
     * @param acquisitionLink 选择的下载链接，包含文件 URL 和 MIME 类型
     * @param username HTTP Basic 认证用户名（可选）
     * @param password HTTP Basic 认证密码（可选）
     * @param client 用于发起请求的 OkHttpClient，默认使用全局 okHttpClient
     * @return 导入后的 Book 实体，已填充 OPDS 元数据
     * @throws IOException 下载失败时抛出
     */
    suspend fun downloadAndImport(
        entry: OpdsEntry,
        acquisitionLink: OpdsLink,
        username: String? = null,
        password: String? = null,
        client: OkHttpClient = okHttpClient
    ): Book {
        val fileName = buildFileName(entry, acquisitionLink)
        val responseBody = try {
            client.newCallResponseBody {
                url(acquisitionLink.href)
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    header("Authorization", Credentials.basic(username, password))
                }
            }
        } catch (e: IOException) {
            throw IOException("OPDS 书籍下载失败: ${e.message}", e)
        }
        val uri = responseBody.byteStream().use { inputStream ->
            LocalBook.saveBookFile(inputStream, fileName)
        }
        val book = LocalBook.importFile(uri)
        fillMetadata(book, entry)
        return book
    }

    /**
     * 从 OpdsEntry 元数据填充 Book 字段。
     * 仅在 OPDS 提供的值非空时覆盖。
     */
    internal fun fillMetadata(book: Book, entry: OpdsEntry) {
        if (entry.title.isNotBlank()) {
            book.name = entry.title
        }
        if (!entry.author.isNullOrBlank()) {
            book.author = entry.author
        }
        if (!entry.summary.isNullOrBlank()) {
            book.intro = entry.summary
        }
        if (!entry.coverUrl.isNullOrBlank()) {
            book.coverUrl = entry.coverUrl
        }
        book.save()
    }

    /**
     * 根据条目标题和 MIME 类型构造下载文件名。
     */
    internal fun buildFileName(entry: OpdsEntry, link: OpdsLink): String {
        val baseName = entry.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val extension = mimeToExtension(link.type)
        return "$baseName.$extension"
    }

    /**
     * 将 MIME 类型映射为文件扩展名。
     */
    internal fun mimeToExtension(mimeType: String?): String {
        return when (mimeType) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "application/x-mobipocket-ebook" -> "mobi"
            "application/x-cbz" -> "cbz"
            "application/x-cbr" -> "cbr"
            "text/plain" -> "txt"
            else -> "epub"
        }
    }
}
