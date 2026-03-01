# 实施计划：安全加固 — 消除 a.gray.BulomiaTGen.f 误报

## 概述

基于需求文档和设计文档，将安全加固方案分解为可增量执行的编码任务。每个任务构建在前一个任务之上，最终将所有变更整合在一起。实现语言为 Kotlin（Android），构建系统为 Gradle。

## 任务

- [x] 1. 权限声明优化与 AndroidManifest 清理
  - [x] 1.1 移除 `READ_PHONE_STATE` 和 `REQUEST_INSTALL_PACKAGES` 权限声明
    - 在 `app/src/main/AndroidManifest.xml` 中删除对应的 `<uses-permission>` 行
    - 使用 `tools:remove` 属性移除第三方库可能引入的这两个权限
    - _需求: 2.1, 2.2, 2.5_
  - [x] 1.2 为 `MANAGE_EXTERNAL_STORAGE` 添加 `android:maxSdkVersion` 限制
    - 将该权限限制在 API 29（Android 10）以下
    - _需求: 2.3_
  - [x] 1.3 编写权限声明单元测试
    - 解析 `AndroidManifest.xml` 验证 `READ_PHONE_STATE` 和 `REQUEST_INSTALL_PACKAGES` 已移除
    - 验证 `MANAGE_EXTERNAL_STORAGE` 包含 `maxSdkVersion` 属性
    - _需求: 2.1, 2.2, 2.3_

- [x] 2. CronetLoader 重构 — 移除远程下载，改为预置加载
  - [x] 2.1 移除 CronetLoader 中的远程下载逻辑
    - 删除 `downloadFileIfNotExist()`、`download()`、`copyFile()` 等方法
    - 删除 `soUrl`、`downloadFile` 等下载相关字段
    - 移除对 `storage.googleapis.com` 的所有引用
    - _需求: 3.1, 3.3_
  - [x] 2.2 简化 `loadLibrary()` 为仅调用 `System.loadLibrary()`
    - 移除所有 `System.load(absolutePath)` 调用
    - 实现加载失败时回退到 GMS Cronet Provider 的逻辑
    - 所有方式均失败时回退到纯 OkHttp
    - _需求: 3.1, 3.2_
  - [x] 2.3 配置构建系统预置 Cronet SO 库
    - 在 `app/build.gradle` 中配置 `jniLibs.srcDirs` 指向预置 SO 目录
    - 确保覆盖 `arm64-v8a` 和 `armeabi-v7a` 架构
    - _需求: 3.5_
  - [x] 2.4 编写属性测试：Cronet SO 预置加载优先
    - **Property 4: Cronet SO 预置加载优先**
    - 模拟不同的 SO 可用性场景，验证加载策略不包含任何网络请求
    - 使用 `Arb.boolean()` 控制 SO 是否预置
    - **验证: 需求 3.1, 3.3**
  - [x] 2.5 编写 CronetLoader 单元测试
    - 验证代码中不包含 `storage.googleapis.com` URL
    - 验证 `loadLibrary()` 仅调用 `System.loadLibrary()`
    - _需求: 3.1, 3.3_

- [x] 3. SSL/TLS 安全配置重构
  - [x] 3.1 修改全局 `okHttpClient` 使用系统默认证书校验
    - 在 `HttpHelper.kt` 中移除全局 `okHttpClient` 对 `unsafeTrustManager` 的使用
    - 改用系统默认的 SSL 证书校验链
    - _需求: 4.1_
  - [x] 3.2 移除 `CronetHelper.disableCertificateVerify()` 方法
    - 删除 `CronetHelper.kt` 中通过反射替换 `X509Util.sDefaultTrustManager` 的方法及其调用
    - 移除 `proguard-rules.pro` 中对应的 `X509Util` keep 规则
    - _需求: 4.4_
  - [x] 3.3 实现 `SSLHelper.createPerSourceClient()` 书源级 SSL 绕过
    - 新增方法，为启用 SSL 绕过的书源创建独立的 OkHttpClient 实例
    - 确保 per-source 的 unsafe client 不影响全局 `okHttpClient`
    - _需求: 4.2, 4.5_
  - [x] 3.4 创建 release 专用 `network_security_config.xml`
    - 新增 `app/src/release/res/xml/network_security_config.xml`，设置 `cleartextTrafficPermitted="false"`
    - 保留 `app/src/debug/res/xml/network_security_config.xml` 允许明文流量
    - _需求: 4.3_
  - [x] 3.5 编写属性测试：域级 SSL 信任隔离
    - **Property 5: 域级 SSL 信任隔离**
    - 生成随机书源配置（启用/不启用 SSL 绕过），验证客户端 SSL 配置隔离
    - 验证启用绕过的客户端不影响全局 `okHttpClient` 的证书校验行为
    - **验证: 需求 4.2, 4.5**
  - [x] 3.6 编写 SSL 配置单元测试
    - 验证全局 `okHttpClient` 不使用 `unsafeTrustManager`
    - 验证 `CronetHelper` 不包含 `disableCertificateVerify` 方法
    - 验证 release `network_security_config.xml` 中 `cleartextTrafficPermitted="false"`
    - _需求: 4.1, 4.3, 4.4_

- [x] 4. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 5. RhinoClassShutter 白名单强化与 JS 引擎安全加固
  - [x] 5.1 将 RhinoClassShutter 从黑名单机制改为白名单 + 黑名单双重检查
    - 定义 `allowedPrefixes` 白名单，仅包含 JS 书源实际需要的 Java 类
    - 定义 `blockedPrefixes` 黑名单，包含 `java.lang.Runtime`、`java.lang.ProcessBuilder`、`java.lang.reflect`、`java.io.File`、`java.net.URLClassLoader`、`dalvik.system.*` 等危险类
    - 重写 `visibleToScripts()` 方法：黑名单优先检查，再检查白名单
    - _需求: 5.1, 5.3, 5.5_
  - [x] 5.2 实现 JS 脚本访问被禁止类时抛出安全异常
    - 当 `visibleToScripts()` 返回 `false` 时，确保 Rhino 引擎抛出 `SecurityException`
    - 在异常中记录警告日志，包含被拒绝的类名
    - _需求: 5.4_
  - [x] 5.3 为 JS 脚本执行添加超时机制
    - 在 `RhinoScriptEngine.kt` 中增加执行超时控制
    - 超时后中断执行并抛出超时异常，释放资源
    - _需求: 5.2_
  - [x] 5.4 编写属性测试：ClassShutter 白名单阻断性
    - **Property 1: ClassShutter 白名单阻断性**
    - 使用 `Arb.string()` 生成随机类名 + 已知白名单/黑名单类名
    - 验证：不在白名单中的类名返回 `false`；在黑名单中的类名无论白名单如何配置都返回 `false`
    - **验证: 需求 5.1, 5.3, 5.5**
  - [x] 5.5 编写属性测试：危险类访问触发安全异常
    - **Property 2: 危险类访问触发安全异常**
    - 从黑名单中随机选取类名，验证 JS 引擎抛出安全异常
    - **验证: 需求 5.4**
  - [x] 5.6 编写属性测试：JS 脚本执行超时终止
    - **Property 3: JS 脚本执行超时终止**
    - 生成无限循环脚本，配置不同超时阈值，验证引擎在超时后正确中断
    - **验证: 需求 5.2**

- [x] 6. 反射调用收敛与替代
  - [x] 6.1 创建统一反射工具类 `ReflectHelper.kt`
    - 实现 `getFieldValue()` 和 `invokeMethod()` 方法，统一管理反射调用
    - 添加 `@SuppressLint("DiscouragedPrivateApi")` 注解
    - _需求: 6.1, 6.4_
  - [x] 6.2 用公开 API 替代可替代的反射调用
    - `MenuExtensions.kt` 中的 `setOptionalIconsVisible` → 使用 `MenuCompat` 公开 API
    - `CronetLoader.kt` 中的 `primaryCpuAbi` → 使用 `Build.SUPPORTED_ABIS[0]`
    - _需求: 6.2_
  - [x] 6.3 将剩余反射调用迁移到 `ReflectHelper`
    - 迁移 `ViewExtensions.kt`、`TintHelper.kt`、`PreferencesExtensions.kt`、`ChangeBookSourceDialog.kt` 中的反射调用
    - 为每个反射目标在 `proguard-rules.pro` 中添加精确的 `-keep` 规则
    - _需求: 6.1, 6.3, 6.4_

- [x] 7. ProGuard/R8 混淆规则优化
  - [x] 7.1 启用 R8 Full Mode
    - 在 `gradle.properties` 中添加 `android.enableR8.fullMode=true`
    - _需求: 1.2_
  - [x] 7.2 优化 ProGuard keep 规则
    - 将 `okhttp3.*{*;}`、`okio.*{*;}` 等宽泛规则缩小为仅保留必要的公开 API
    - 精确化 `com.jayway.jsonpath`、`org.jsoup` 等库的 keep 规则
    - 添加 `-flattenpackagehierarchy` 规则将包层级扁平化
    - 验证 Log 移除规则 `-assumenosideeffects` 生效
    - _需求: 1.1, 1.3, 1.4, 1.5_
  - [x] 7.3 编写 ProGuard 规则单元测试
    - 验证 `proguard-rules.pro` 中包含 `-flattenpackagehierarchy` 规则
    - 验证 Log 移除规则存在
    - _需求: 1.1, 1.3, 1.5_

- [x] 8. 检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 9. APK 签名加固
  - [x] 9.1 验证并加固签名配置
    - 验证当前签名密钥强度为 RSA 2048 位或更高
    - 确认 V1-V4 签名方案均已启用
    - _需求: 7.1, 7.2_
  - [x] 9.2 将签名密钥库移出版本控制
    - 在 `.gitignore` 中添加 `legado-release.jks` 和包含密码的属性文件
    - 修改 `app/build.gradle` 签名配置从环境变量读取密钥库路径和密码
    - _需求: 7.3_

- [x] 10. 构建产物安全验证
  - [x] 10.1 创建 `SecurityVerifyPlugin.gradle` 自定义验证任务
    - 实现 `verifyReleaseSecurity` Gradle task
    - 使用 `aapt2 dump permissions` 检查权限清单
    - 扫描 DEX 中的未混淆敏感类名模式
    - 输出签名信息摘要（签名算法、证书指纹、签名方案版本）
    - 生成安全验证报告
    - _需求: 8.1, 8.2, 8.3, 8.4_
  - [x] 10.2 在 `app/build.gradle` 中集成安全验证任务
    - 将 `verifyReleaseSecurity` 挂载到 release 构建流程
    - _需求: 8.1_
  - [x] 10.3 编写属性测试：敏感类名模式检测
    - **Property 6: 敏感类名模式检测**
    - 生成包含/不包含敏感模式的类名列表，验证检测逻辑的准确性
    - 使用 `Arb.string()` + 敏感关键词注入
    - **验证: 需求 8.4**

- [x] 11. 集成与整合
  - [x] 11.1 整合所有变更并验证构建
    - 确保所有模块变更协同工作，release 构建成功
    - 验证 `mapping.txt` 正确生成，混淆映射可追溯
    - _需求: 1.6_
  - [x] 11.2 构建后输出签名信息摘要
    - 在 release 构建完成后自动输出 APK 签名信息
    - _需求: 7.4_

- [x] 12. 最终检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 属性测试使用 Kotest Property-Based Testing 框架（`io.kotest:kotest-property`）
- 单元测试使用 JUnit 5 + Robolectric
