# 实现计划：OPDS 客户端支持

## 概述

基于设计文档，将 OPDS 客户端功能分解为增量式编码任务。使用 Kotlin 实现，依赖 Readium OPDS 库进行 Feed 解析，复用项目现有的 Room、OkHttp、Gson 等基础设施。

## 任务

- [x] 1. 添加依赖和项目配置
  - [x] 1.1 在 `gradle/libs.versions.toml` 中添加 Readium OPDS 和 Kotest Property Testing 依赖版本
    - 添加 `readium-opds` 和 `readium-shared` 依赖（通过 JitPack 或 Maven Central）
    - 添加 `kotest-property` 测试依赖
    - _Requirements: 1.1_
  - [x] 1.2 在 `app/build.gradle` 中引入新依赖
    - 添加 `implementation` 引用 readium-opds 和 readium-shared
    - 添加 `testImplementation` 引用 kotest-property
    - _Requirements: 1.1_

- [x] 2. 创建 OPDS 数据模型
  - [x] 2.1 创建 `OpdsLink` 数据类
    - 在 `io.legado.app.data.entities` 包下创建 `OpdsLink.kt`
    - 包含 `href`、`type`、`rel`、`title`、`length` 字段
    - 实现 `formatName` 和 `isAcquisition` 计算属性
    - _Requirements: 1.6_
  - [x] 2.2 创建 `OpdsEntry` 数据类
    - 在 `io.legado.app.data.entities` 包下创建 `OpdsEntry.kt`
    - 包含 `id`、`title`、`author`、`summary`、`content`、`updated`、`coverUrl`、`links`、`acquisitionLinks` 字段
    - 实现 `isNavigation`、`navigationUrl`、`hasAcquisitions` 计算属性
    - _Requirements: 1.4, 1.6_
  - [x] 2.3 创建 `OpdsFeed` 数据类
    - 在 `io.legado.app.data.entities` 包下创建 `OpdsFeed.kt`
    - 包含 `id`、`title`、`updated`、`author`、`entries`、`links`、`isNavigation`、`searchUrl` 字段
    - 实现 `nextPageUrl` 计算属性
    - _Requirements: 1.3, 1.4, 1.5_
  - [x] 2.4 编写 OpdsFeed JSON 往返属性测试
    - **Property 3: OpdsFeed JSON round-trip consistency**
    - 使用 Kotest Property Testing 生成随机 OpdsFeed 对象
    - 验证 Gson 序列化后再反序列化产生等价对象
    - 最少 100 次迭代
    - **Validates: Requirements 2.1, 2.2, 2.3**

- [x] 3. 创建 OpdsSource 实体和 DAO
  - [x] 3.1 创建 `OpdsSource` Room Entity
    - 在 `io.legado.app.data.entities` 包下创建 `OpdsSource.kt`
    - 包含 `sourceUrl`（主键）、`sourceName`、`username`、`password`、`sortOrder`、`lastAccessTime`、`enabled` 字段
    - _Requirements: 6.1, 6.6_
  - [x] 3.2 创建 `OpdsSourceDao` 接口
    - 在 `io.legado.app.data.dao` 包下创建 `OpdsSourceDao.kt`
    - 实现 `getAll`、`flowAll`、`getByUrl`、`insert`、`update`、`delete`、`has` 方法
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [x] 3.3 在 `AppDatabase` 中注册 OpdsSource 实体和 OpdsSourceDao
    - 添加 `OpdsSource` 到 `@Database` 注解的 entities 列表
    - 添加 `abstract fun opdsSourceDao(): OpdsSourceDao`
    - 创建数据库迁移脚本
    - _Requirements: 6.4_
  - [x] 3.4 编写 OpdsSource CRUD 往返属性测试
    - **Property 7: OpdsSource CRUD round-trip**
    - 使用 Room in-memory 数据库测试
    - 生成随机 OpdsSource，插入后查询验证等价性
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4**

- [x] 4. Checkpoint - 确保数据模型和数据库编译通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 5. 实现 OPDS 解析器
  - [x] 5.1 创建 `OpdsParser` 对象
    - 在 `io.legado.app.model.opds` 包下创建 `OpdsParser.kt`
    - 实现 `parseFeed(content: String, url: String): OpdsFeed` 方法
    - 内部调用 Readium `OPDS1Parser.parse()` 解析 OPDS 1.x Feed
    - 将 Readium `Feed` 对象转换为内部 `OpdsFeed` 数据模型
    - 处理解析异常，返回描述性错误信息
    - _Requirements: 1.1, 1.2, 1.3, 1.7_
  - [x] 5.2 实现 OpenSearch Descriptor 解析
    - 在 `OpdsParser` 中添加 `parseOpenSearchDescriptor(content: String): String?` 方法
    - 使用 XmlPullParser 解析 OpenSearch XML，提取 URL 模板
    - _Requirements: 5.1_
  - [x] 5.3 实现 URL 验证工具方法
    - 在 `OpdsParser` 或独立工具类中添加 `isValidOpdsUrl(url: String): Boolean`
    - 验证 URL 以 http:// 或 https:// 开头且包含有效主机名
    - _Requirements: 6.5_
  - [x] 5.4 实现 OpenSearch URL 模板替换方法
    - 在 `OpdsParser` 中添加 `buildSearchUrl(template: String, query: String): String`
    - 将模板中的 `{searchTerms}` 替换为 URL 编码后的搜索关键词
    - _Requirements: 5.2_
  - [x] 5.5 编写 OPDS Feed 转换完整性属性测试
    - **Property 1: OPDS Feed conversion preserves all fields**
    - 使用预置的有效 OPDS XML 模板，随机填充字段值
    - 验证解析后的 OpdsFeed 包含所有预期字段
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6**
  - [x] 5.6 编写无效 XML 解析错误属性测试
    - **Property 2: Invalid XML produces parse error**
    - 生成随机无效字符串，验证解析器不崩溃且返回错误
    - **Validates: Requirements 1.7**
  - [x] 5.7 编写 OpenSearch URL 模板替换属性测试
    - **Property 5: OpenSearch URL template substitution**
    - 生成随机 URL 模板和搜索关键词，验证替换结果包含关键词
    - **Validates: Requirements 5.2**
  - [x] 5.8 编写 URL 验证属性测试
    - **Property 6: URL validation correctness**
    - 生成随机字符串，验证 URL 验证函数的正确性
    - **Validates: Requirements 6.5**

- [x] 6. Checkpoint - 确保解析器和所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 7. 实现 OPDS 控制器和下载器
  - [x] 7.1 创建 `OpdsController` 对象
    - 在 `io.legado.app.api.controller` 包下创建 `OpdsController.kt`
    - 实现 `fetchFeed(url, username?, password?): OpdsFeed` 方法
    - 使用 OkHttp 发起 HTTP 请求，支持 HTTP Basic 认证
    - 调用 `OpdsParser.parseFeed()` 解析响应
    - 处理网络异常和解析异常
    - _Requirements: 3.1, 3.5, 3.6, 6.6_
  - [x] 7.2 实现搜索功能
    - 在 `OpdsController` 中添加 `search(searchUrlTemplate, query, username?, password?): OpdsFeed`
    - 调用 `OpdsParser.buildSearchUrl()` 构造搜索 URL
    - 请求并解析搜索结果
    - _Requirements: 5.1, 5.2_
  - [x] 7.3 创建 `OpdsDownloader` 对象
    - 在 `io.legado.app.model.opds` 包下创建 `OpdsDownloader.kt`
    - 实现 `downloadAndImport(entry, acquisitionLink, username?, password?): Book`
    - 使用 OkHttp 下载文件到临时目录
    - 调用 `LocalBook.saveBookFile()` 和 `LocalBook.importFile()` 导入
    - 从 OpdsEntry 元数据填充 Book 的 name、author、intro、coverUrl
    - _Requirements: 4.2, 4.3, 4.4_
  - [x] 7.4 编写 OpdsEntry 到 Book 元数据保留属性测试
    - **Property 4: OpdsEntry to Book metadata preservation**
    - 生成随机 OpdsEntry，验证转换后 Book 的元数据字段一致
    - **Validates: Requirements 4.3**

- [x] 8. 实现 OPDS 源管理 UI
  - [x] 8.1 创建 `OpdsActivity`
    - 在 `io.legado.app.ui.opds` 包下创建 `OpdsActivity.kt` 和对应布局文件
    - 使用 ViewPager2 包含源管理和浏览两个 Tab
    - _Requirements: 6.1_
  - [x] 8.2 创建 `OpdsSourceFragment` 和源编辑对话框
    - 实现 OPDS 源列表展示（RecyclerView）
    - 实现添加/编辑源对话框（名称、URL、用户名、密码输入）
    - 实现删除源功能（长按或滑动删除）
    - 调用 `OpdsSourceDao` 进行 CRUD 操作
    - 添加源时调用 URL 验证
    - _Requirements: 6.1, 6.2, 6.3, 6.5, 6.6_

- [x] 9. 实现 OPDS 目录浏览 UI
  - [x] 9.1 创建 `OpdsBrowseFragment`
    - 在 `io.legado.app.ui.opds` 包下创建 `OpdsBrowseFragment.kt` 和对应布局文件
    - 实现 OPDS Feed 条目列表展示
    - 区分导航条目和书籍条目的显示样式
    - 实现导航栈（点击导航条目进入子 Feed，支持返回）
    - 实现分页加载（检测 nextPageUrl，滚动到底部加载更多）
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  - [x] 9.2 实现搜索 UI
    - 在 `OpdsBrowseFragment` 中添加搜索栏
    - 根据 `OpdsFeed.searchUrl` 是否为空控制搜索栏显示/隐藏
    - 提交搜索时调用 `OpdsController.search()` 并展示结果
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [x] 9.3 创建 `OpdsBookDetailDialog`
    - 实现书籍详情弹窗，显示书名、作者、简介、封面
    - 列出可用的下载格式（从 acquisitionLinks 获取）
    - 无 acquisitionLinks 时禁用下载按钮并提示
    - 点击下载调用 `OpdsDownloader.downloadAndImport()`
    - 显示下载进度和错误处理（网络错误时允许重试）
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 10. 集成和入口连接
  - [x] 10.1 在主界面添加 OPDS 入口
    - 在应用主界面（如"发现"页面或侧边栏）添加 OPDS 入口按钮
    - 点击跳转到 `OpdsActivity`
    - 在 `AndroidManifest.xml` 中注册 `OpdsActivity`
    - _Requirements: 3.1_
  - [x] 10.2 处理网络错误和格式错误的 UI 提示
    - 在 `OpdsBrowseFragment` 中实现错误状态展示
    - 网络错误显示重试按钮
    - 格式错误显示提示信息
    - _Requirements: 3.5, 3.6, 4.4_

- [x] 11. 最终 Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

## 备注

- 所有任务均为必需，包括测试任务
- 每个任务引用了具体的需求编号以确保可追溯性
- Checkpoint 任务用于增量验证
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
