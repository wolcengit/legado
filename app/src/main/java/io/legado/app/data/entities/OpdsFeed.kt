package io.legado.app.data.entities

data class OpdsFeed(
    val id: String,
    val title: String,
    val updated: String? = null,
    val author: String? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    val isNavigation: Boolean = false,
    val searchUrl: String? = null
) {
    /** 获取下一页链接 */
    val nextPageUrl: String?
        get() = links.firstOrNull { it.rel == "next" }?.href
}
