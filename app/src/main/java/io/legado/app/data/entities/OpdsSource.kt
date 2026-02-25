package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "opdsSources")
data class OpdsSource(
    @PrimaryKey
    var sourceUrl: String = "",
    // 源名称
    var sourceName: String = "",
    // HTTP Basic 认证用户名
    var username: String? = null,
    // HTTP Basic 认证密码
    var password: String? = null,
    // 排序顺序
    var sortOrder: Int = 0,
    // 最后访问时间
    var lastAccessTime: Long = 0L,
    // 是否启用
    var enabled: Boolean = true
) : Parcelable
