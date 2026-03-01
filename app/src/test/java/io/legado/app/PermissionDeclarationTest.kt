package io.legado.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 权限声明单元测试
 * 验证 AndroidManifest.xml 中的敏感权限已正确处理
 *
 * Validates: Requirements 2.1, 2.2, 2.3
 */
class PermissionDeclarationTest {

    private data class PermissionEntry(
        val name: String,
        val toolsNode: String?,
        val maxSdkVersion: String?
    )

    private lateinit var permissions: List<PermissionEntry>

    @Before
    fun setUp() {
        val manifestFile = java.io.File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(manifestFile)
        val nodeList = doc.getElementsByTagName("uses-permission")

        permissions = (0 until nodeList.length).map { i ->
            val element = nodeList.item(i) as org.w3c.dom.Element
            PermissionEntry(
                name = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name"
                ),
                toolsNode = element.getAttributeNS(
                    "http://schemas.android.com/tools", "node"
                ).ifEmpty { null },
                maxSdkVersion = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "maxSdkVersion"
                ).ifEmpty { null }
            )
        }
    }

    /**
     * Validates: Requirement 2.1
     * READ_PHONE_STATE 应通过 tools:node="remove" 移除，而非作为常规权限声明
     */
    @Test
    fun readPhoneState_shouldBeMarkedForRemoval() {
        val entry = permissions.find {
            it.name == "android.permission.READ_PHONE_STATE"
        }
        assertNotNull("READ_PHONE_STATE should be declared with tools:node=\"remove\"", entry)
        assertEquals(
            "READ_PHONE_STATE must have tools:node=\"remove\"",
            "remove",
            entry!!.toolsNode
        )
    }

    /**
     * Validates: Requirement 2.2
     * REQUEST_INSTALL_PACKAGES 应通过 tools:node="remove" 移除，而非作为常规权限声明
     */
    @Test
    fun requestInstallPackages_shouldBeMarkedForRemoval() {
        val entry = permissions.find {
            it.name == "android.permission.REQUEST_INSTALL_PACKAGES"
        }
        assertNotNull("REQUEST_INSTALL_PACKAGES should be declared with tools:node=\"remove\"", entry)
        assertEquals(
            "REQUEST_INSTALL_PACKAGES must have tools:node=\"remove\"",
            "remove",
            entry!!.toolsNode
        )
    }

    /**
     * Validates: Requirement 2.3
     * MANAGE_EXTERNAL_STORAGE 应包含 maxSdkVersion 属性以限制 API 级别范围
     */
    @Test
    fun manageExternalStorage_shouldHaveMaxSdkVersion() {
        val entry = permissions.find {
            it.name == "android.permission.MANAGE_EXTERNAL_STORAGE"
        }
        assertNotNull("MANAGE_EXTERNAL_STORAGE should be declared", entry)
        assertNotNull(
            "MANAGE_EXTERNAL_STORAGE must have android:maxSdkVersion attribute",
            entry!!.maxSdkVersion
        )
        assertEquals(
            "MANAGE_EXTERNAL_STORAGE maxSdkVersion should be 29",
            "29",
            entry.maxSdkVersion
        )
    }
}
