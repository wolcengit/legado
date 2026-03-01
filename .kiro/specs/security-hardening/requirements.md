# 需求文档：安全加固 — 消除 a.gray.BulomiaTGen.f 误报

## 简介

legado（阅读）是一款开源 Android 阅读应用，编译发布后在部分手机管家中被误报为恶意病毒 `a.gray.BulomiaTGen.f`。该病毒签名属于灰色软件（Grayware）分类，通常由以下行为模式触发：动态加载原生库（SO 文件）、运行时反射访问私有 API、内嵌 JavaScript 引擎执行动态代码、SSL 证书校验绕过、以及声明敏感权限组合。

通过对项目代码的分析，识别出以下主要触发因素：

1. **CronetLoader 动态下载并加载 SO 库**：从远程服务器下载 `libcronet.*.so` 并通过 `System.load()` 加载，这是典型的灰色软件行为特征
2. **SSLHelper 全局禁用证书校验**：`unsafeTrustManager` 信任所有证书，`disableCertificateVerify` 通过反射替换 Cronet 的信任管理器
3. **Rhino JavaScript 引擎**：内嵌完整的 JS 运行时，支持动态执行脚本代码
4. **大量反射调用**：通过 `getDeclaredField`/`getDeclaredMethod` + `setAccessible(true)` 访问私有 API
5. **敏感权限组合**：`READ_PHONE_STATE` + `MANAGE_EXTERNAL_STORAGE` + `REQUEST_INSTALL_PACKAGES`（虽然未使用但已声明）+ `INTERNET` 的组合是典型的恶意软件权限特征
6. **ProGuard 混淆规则过于宽松**：大量 `-keep` 规则导致关键代码未被充分混淆

本需求文档定义消除误报所需的安全加固措施。

## 术语表

- **构建系统（Build_System）**：基于 Gradle 的 Android 项目构建流程，包括编译、混淆、签名和打包
- **混淆器（Obfuscator）**：R8/ProGuard 代码混淆和优化工具
- **权限声明（Permission_Manifest）**：AndroidManifest.xml 中的权限声明配置
- **签名配置（Signing_Config）**：APK 签名相关的密钥库和签名方案配置
- **网络安全配置（Network_Security_Config）**：Android 网络安全策略配置文件
- **SSL 助手（SSL_Helper）**：`SSLHelper.kt` 中的 SSL/TLS 连接辅助工具类
- **Cronet 加载器（Cronet_Loader）**：`CronetLoader.kt` 中负责下载和加载 Cronet SO 库的组件
- **JS 引擎（JS_Engine）**：基于 Mozilla Rhino 的 JavaScript 脚本执行引擎
- **类过滤器（Class_Shutter）**：`RhinoClassShutter.kt` 中限制 JS 访问 Java 类的安全组件
- **安全扫描器（Security_Scanner）**：手机管家等安全软件中的病毒检测引擎

## 需求

### 需求 1：ProGuard/R8 混淆规则优化

**用户故事：** 作为开发者，我希望优化代码混淆规则，使得编译产物中的类名、方法名和包结构被充分混淆，从而降低安全扫描器的误报概率。

#### 验收标准

1. THE 混淆器 SHALL 在 release 构建中对所有非必要保留的类和方法执行完整的名称混淆
2. WHEN release 构建执行时，THE 构建系统 SHALL 启用 R8 的完整优化模式（full mode），包括类合并、方法内联和无用代码移除
3. THE 混淆器 SHALL 将应用包名层级扁平化，使混淆后的类分布在单一或少量包路径下
4. WHEN ProGuard 规则中存在过于宽泛的 `-keep` 规则时，THE 构建系统 SHALL 将其缩小为仅保留通过反射或序列化实际访问的类和成员
5. THE 混淆器 SHALL 移除 release 构建产物中所有 `android.util.Log` 的调用（已有规则需验证生效）
6. IF 混淆规则变更导致运行时 `ClassNotFoundException` 或 `NoSuchMethodException`，THEN THE 构建系统 SHALL 通过 mapping.txt 提供可追溯的混淆映射

### 需求 2：敏感权限声明优化

**用户故事：** 作为开发者，我希望移除不必要的敏感权限声明，并对必要权限添加合理的使用说明，从而减少安全扫描器将应用标记为灰色软件的风险。

#### 验收标准

1. THE 权限声明 SHALL 移除 `READ_PHONE_STATE` 权限，除非应用存在明确的电话状态监听功能需求
2. THE 权限声明 SHALL 移除 `REQUEST_INSTALL_PACKAGES` 权限（项目代码中未发现任何 APK 安装相关逻辑）
3. WHEN 应用需要 `MANAGE_EXTERNAL_STORAGE` 权限时，THE 权限声明 SHALL 添加 `<uses-permission>` 的 `android:maxSdkVersion` 属性，将其限制在确实需要的 API 级别范围内
4. THE 权限声明 SHALL 对每个保留的敏感权限在代码中实现运行时动态申请，而非仅在 Manifest 中静态声明
5. THE 权限声明 SHALL 使用 `<uses-permission>` 的 `tools:remove` 属性移除第三方库引入的不必要权限

### 需求 3：动态库加载安全加固

**用户故事：** 作为开发者，我希望对 Cronet SO 库的加载方式进行安全加固，使其不再表现出动态下载并执行代码的灰色软件行为特征。

#### 验收标准

1. THE Cronet_Loader SHALL 优先使用应用内预置的 SO 库文件，而非从远程服务器动态下载
2. WHEN 应用内预置 SO 库不可用时，THE Cronet_Loader SHALL 回退到使用 Android 系统提供的 Cronet 实现（`com.google.android.gms` 提供的 Cronet Provider）
3. THE Cronet_Loader SHALL 移除从 `storage.googleapis.com` 动态下载 SO 文件的逻辑
4. IF 必须保留远程下载 SO 库的能力，THEN THE Cronet_Loader SHALL 对下载的文件执行 SHA-256 签名校验，并仅从应用自有的可信域名下载
5. THE 构建系统 SHALL 将所需的 Cronet SO 库以 `jniLibs` 方式预置在 APK 中，覆盖 `arm64-v8a` 和 `armeabi-v7a` 架构

### 需求 4：SSL/TLS 安全配置加固

**用户故事：** 作为开发者，我希望消除全局禁用 SSL 证书校验的不安全做法，使安全扫描器不再将应用标记为存在中间人攻击风险。

#### 验收标准

1. THE SSL_Helper SHALL 在 release 构建中移除 `unsafeTrustManager` 的全局使用，改为使用系统默认的证书校验链
2. WHEN 用户访问的书源服务器使用自签名证书时，THE SSL_Helper SHALL 提供针对特定域名的证书信任配置，而非全局信任所有证书
3. THE Network_Security_Config SHALL 在 release 构建中将 `cleartextTrafficPermitted` 设置为 `false`，仅在 `debug-overrides` 中允许明文流量
4. THE SSL_Helper SHALL 移除通过反射替换 Cronet X509Util 信任管理器的 `disableCertificateVerify()` 方法
5. IF 特定书源需要跳过证书校验，THEN THE SSL_Helper SHALL 将该行为限定在该书源的网络请求范围内，不影响全局网络安全策略

### 需求 5：JavaScript 引擎安全加固

**用户故事：** 作为开发者，我希望加强 Rhino JS 引擎的安全沙箱限制，使安全扫描器不再将动态脚本执行标记为恶意行为。

#### 验收标准

1. THE Class_Shutter SHALL 采用白名单机制，仅允许 JS 脚本访问明确授权的 Java 类，拒绝所有未列入白名单的类访问
2. THE JS_Engine SHALL 对脚本执行设置内存限制和执行时间限制，防止恶意脚本消耗过多资源
3. THE Class_Shutter SHALL 阻止 JS 脚本访问 `java.lang.Runtime`、`java.lang.ProcessBuilder`、`java.io.File`（写操作）、`java.net.URLClassLoader` 和 `dalvik.system.*` 等危险类
4. WHEN JS 脚本尝试访问被禁止的类时，THE JS_Engine SHALL 记录警告日志并抛出安全异常
5. THE JS_Engine SHALL 禁止通过 `java.lang.reflect` 包进行反射操作，防止脚本绕过类过滤器的限制

### 需求 6：反射调用收敛与替代

**用户故事：** 作为开发者，我希望减少应用中的反射调用数量，用标准 API 替代不必要的反射访问，从而降低安全扫描器的行为特征匹配分数。

#### 验收标准

1. THE 构建系统 SHALL 将所有反射调用集中到统一的工具类中管理，避免在业务代码中散布反射调用
2. WHEN 存在可用的公开 API 替代方案时，THE 构建系统 SHALL 使用公开 API 替代反射调用（例如使用 `MenuCompat.setGroupDividerEnabled` 替代反射访问 `setOptionalIconsVisible`）
3. THE 混淆器 SHALL 对无法替代的反射调用目标类和成员添加精确的 `-keep` 规则，确保反射调用在混淆后仍然有效
4. THE 构建系统 SHALL 对所有保留的反射调用添加 `@SuppressLint("DiscouragedPrivateApi")` 或自定义注解标记，便于后续审计和追踪

### 需求 7：APK 签名加固

**用户故事：** 作为开发者，我希望使用更强的签名方案对 APK 进行签名，使安全扫描器能够验证应用的完整性和来源可信度。

#### 验收标准

1. THE Signing_Config SHALL 同时启用 V1（JAR 签名）、V2（APK 签名方案 v2）、V3（APK 签名方案 v3）和 V4 签名方案（当前配置已满足，需验证签名密钥强度）
2. THE Signing_Config SHALL 使用 RSA 2048 位或更高强度的签名密钥
3. WHEN 签名密钥库文件（`legado-release.jks`）存在于项目根目录时，THE 构建系统 SHALL 在 CI/CD 流程中将其移至安全的密钥管理服务，不将其提交到版本控制系统
4. THE 构建系统 SHALL 在 release 构建完成后输出 APK 签名信息摘要，包括签名算法、证书指纹和签名方案版本

### 需求 8：构建产物安全验证

**用户故事：** 作为开发者，我希望在构建流程中集成自动化安全检查，确保每次发布的 APK 不会触发主流安全扫描器的误报。

#### 验收标准

1. THE 构建系统 SHALL 在 release 构建后自动执行 APK 分析，检查是否包含已知的误报触发模式（如未混淆的敏感类名、动态加载代码路径）
2. WHEN release APK 构建完成时，THE 构建系统 SHALL 输出权限清单报告，列出 APK 中声明的所有权限及其来源（应用自身或第三方库）
3. THE 构建系统 SHALL 使用 `aapt2 dump permissions` 验证最终 APK 中不包含已移除的敏感权限
4. IF 构建产物中检测到未混淆的敏感类名模式（如包含 `Runtime`、`ProcessBuilder`、`ClassLoader` 等关键词的未混淆类），THEN THE 构建系统 SHALL 输出警告信息
