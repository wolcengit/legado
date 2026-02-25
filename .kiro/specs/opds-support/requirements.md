# 需求文档

## 简介

为阅读（legado）应用添加 OPDS 客户端支持。OPDS（Open Publication Distribution System）是一种基于 Atom/XML 的数字出版物分发协议，广泛用于电子书目录的发布和发现。本功能使用 Readium OPDS 开源库实现 OPDS Feed 解析，允许用户在应用中连接外部 OPDS 目录（如 Calibre OPDS、COPS、Komga 等），浏览目录结构、搜索书籍，并将书籍下载导入到本地书架。

## 术语表

- **OPDS_Catalog**：符合 OPDS 规范的 Atom XML Feed，包含导航条目和获取条目
- **Navigation_Feed**：OPDS 导航类型的 Feed，用于浏览分类目录结构
- **Acquisition_Feed**：OPDS 获取类型的 Feed，包含书籍条目及下载链接
- **OPDS_Entry**：OPDS Feed 中的单个条目（Atom entry），代表一本书或一个分类导航
- **OPDS_Client**：应用内的 OPDS 客户端模块，用于连接和解析外部 OPDS 目录
- **Readium_OPDS**：Readium 开源库的 OPDS 模块，提供 OPDS 1.x 和 2.0 的解析能力
- **OPDS_Feed_Model**：解析后的 OPDS Feed 结构化数据模型，基于 Readium 的数据结构进行适配
- **OpenSearch_Descriptor**：基于 OpenSearch 规范的搜索描述文档，定义 OPDS 目录的搜索接口
- **Acquisition_Link**：OPDS_Entry 中的获取链接，包含书籍文件的下载 URL 和 MIME 类型
- **Book_Entity**：应用数据库中的书籍实体对象
- **HttpServer**：应用现有的基于 NanoHTTPD 的 HTTP 服务器

## 需求

### 需求 1：OPDS Feed 解析

**用户故事：** 作为开发者，我希望有可靠的 OPDS Atom XML 解析组件，以便客户端能正确解析外部 OPDS 目录返回的数据。

#### 验收标准

1. THE OPDS_Client SHALL 使用 Readium_OPDS 库解析 OPDS 1.x 和 2.0 格式的 Feed
2. THE OPDS_Client SHALL 将 Readium 解析结果转换为应用内部的 OPDS_Feed_Model 数据对象
3. THE OPDS_Feed_Model SHALL 正确区分 Navigation_Feed 和 Acquisition_Feed 两种 Feed 类型
4. THE OPDS_Feed_Model SHALL 包含每个条目的 `id`、`title`、`author`、`summary`、封面图片链接和所有链接信息
5. THE OPDS_Feed_Model SHALL 包含分页链接信息（`next`、`previous` 等关系）
6. THE OPDS_Feed_Model SHALL 从 Acquisition_Link 中提取下载 URL 和 MIME 类型（如 `application/epub+zip`、`application/pdf`）
7. IF 输入的 XML 格式不合法或不是有效的 OPDS Feed，THEN THE OPDS_Client SHALL 返回描述性的解析错误信息

### 需求 2：OPDS Feed 数据模型序列化

**用户故事：** 作为开发者，我希望能将 OPDS_Feed_Model 序列化为 JSON 并反序列化回来，以便进行缓存和往返测试验证数据模型的正确性。

#### 验收标准

1. THE OPDS_Feed_Model SHALL 支持序列化为 JSON 字符串
2. THE OPDS_Feed_Model SHALL 支持从 JSON 字符串反序列化
3. FOR ALL 有效的 OPDS_Feed_Model 对象，序列化为 JSON 后再反序列化 SHALL 产生等价的数据对象（往返一致性）

### 需求 3：OPDS 目录浏览

**用户故事：** 作为用户，我希望在应用中添加外部 OPDS 目录源并浏览其内容，以便发现和获取新书籍。

#### 验收标准

1. WHEN 用户添加一个 OPDS 目录 URL 时，THE OPDS_Client SHALL 请求该 URL 并使用 Readium_OPDS 解析返回的 OPDS_Catalog
2. WHEN 解析成功时，THE OPDS_Client SHALL 显示 OPDS_Catalog 中的导航条目和书籍条目
3. WHEN 用户点击一个导航类型的 OPDS_Entry 时，THE OPDS_Client SHALL 请求并显示该条目链接指向的子 Feed
4. WHEN OPDS_Catalog 包含分页链接时，THE OPDS_Client SHALL 支持加载下一页内容
5. IF OPDS 目录 URL 无法访问，THEN THE OPDS_Client SHALL 显示网络错误提示信息
6. IF 返回的内容不是有效的 OPDS Feed，THEN THE OPDS_Client SHALL 显示格式错误提示信息

### 需求 4：OPDS 书籍详情与下载

**用户故事：** 作为用户，我希望从外部 OPDS 目录中查看书籍详情并将书籍下载导入到本地书架，以便离线阅读。

#### 验收标准

1. WHEN 用户在 OPDS 目录中选择一本书籍时，THE OPDS_Client SHALL 显示该书籍的详细信息，包括书名、作者、简介和可用的下载格式列表
2. WHEN 用户选择下载一本书籍时，THE OPDS_Client SHALL 下载书籍文件并将其导入到本地书架
3. WHEN 书籍下载完成时，THE OPDS_Client SHALL 在本地书架中创建对应的 Book_Entity，保留书名、作者、简介和封面信息
4. IF 下载过程中发生网络错误，THEN THE OPDS_Client SHALL 显示错误信息并允许用户重试
5. IF OPDS_Entry 不包含任何 Acquisition_Link，THEN THE OPDS_Client SHALL 禁用下载按钮并提示无可用下载

### 需求 5：OPDS 目录搜索

**用户故事：** 作为用户，我希望在外部 OPDS 目录中搜索书籍，以便快速找到想要的书。

#### 验收标准

1. WHEN OPDS_Catalog 提供 OpenSearch_Descriptor 链接时，THE OPDS_Client SHALL 启用搜索功能
2. WHEN 用户输入搜索关键词并提交时，THE OPDS_Client SHALL 根据 OpenSearch_Descriptor 构造搜索 URL，请求并显示搜索结果
3. WHEN OPDS_Catalog 未提供 OpenSearch_Descriptor 链接时，THE OPDS_Client SHALL 隐藏搜索入口
4. WHEN 搜索结果包含分页链接时，THE OPDS_Client SHALL 支持加载更多搜索结果

### 需求 6：OPDS 目录源管理

**用户故事：** 作为用户，我希望管理我添加的 OPDS 目录源，以便维护和组织我的书籍来源。

#### 验收标准

1. THE OPDS_Client SHALL 允许用户添加新的 OPDS 目录源，需提供目录名称和 URL
2. THE OPDS_Client SHALL 允许用户编辑已有 OPDS 目录源的名称和 URL
3. THE OPDS_Client SHALL 允许用户删除已有的 OPDS 目录源
4. THE OPDS_Client SHALL 将 OPDS 目录源信息持久化存储到本地数据库
5. WHEN 用户添加 OPDS 目录源时，THE OPDS_Client SHALL 验证 URL 格式的合法性
6. WHERE OPDS 目录需要 HTTP Basic 认证，THE OPDS_Client SHALL 支持用户配置用户名和密码
