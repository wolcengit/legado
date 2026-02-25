package io.legado.app.data.entities

data class OpdsLink(
    val href: String,
    val type: String? = null,
    val rel: String? = null,
    val title: String? = null,
    val length: Long? = null
) {
    /** 获取人类可读的格式名称 */
    val formatName: String
        get() = when (type) {
            "application/epub+zip" -> "EPUB"
            "application/pdf" -> "PDF"
            "application/x-mobipocket-ebook" -> "MOBI"
            "application/x-cbz" -> "CBZ"
            "application/x-cbr" -> "CBR"
            "text/plain" -> "TXT"
            else -> type ?: "未知格式"
        }

    /** 是否为获取链接 */
    val isAcquisition: Boolean
        get() = rel?.contains("acquisition") == true ||
                rel == "http://opds-spec.org/acquisition" ||
                rel == "http://opds-spec.org/acquisition/open-access"
}
