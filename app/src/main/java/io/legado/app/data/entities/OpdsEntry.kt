package io.legado.app.data.entities

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val updated: String? = null,
    val coverUrl: String? = null,
    val links: List<OpdsLink> = emptyList(),
    val acquisitionLinks: List<OpdsLink> = emptyList()
) {
    /** 是否为导航条目（有子 Feed 链接，且没有可下载文件） */
    val isNavigation: Boolean
        get() = !hasAcquisitions && links.any {
            it.type?.contains("opds-catalog") == true ||
            it.rel == "subsection"
        }

    /** 获取导航链接 URL */
    val navigationUrl: String?
        get() = links.firstOrNull {
            it.type?.contains("opds-catalog") == true ||
            it.rel == "subsection"
        }?.href

    /** 是否有可下载的书籍文件 */
    val hasAcquisitions: Boolean
        get() = acquisitionLinks.isNotEmpty()
}
