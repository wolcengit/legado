# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames

# 这句话能够使我们的项目混淆后产生映射文件
# 包含有类名->混淆后类名的映射关系
-verbose

# 保留Annotation不混淆
-keepattributes *Annotation*,InnerClasses

# 避免混淆泛型
-keepattributes Signature

# 指定混淆是采用的算法，后面的参数是一个过滤器
# 这个过滤器是谷歌推荐的算法，一般不做更改
-optimizations !code/simplification/cast,!field/*,!class/merging/*

-flattenpackagehierarchy

#############################################
#
# Android开发中一些需要保留的公共部分
#
#############################################
# 屏蔽错误Unresolved class name
#noinspection ShrinkerUnresolvedReference

# 移除Log类打印各个等级日志的代码，打正式包的时候可以做为禁log使用，这里可以作为禁止log打印的功能使用
# 记得proguard-android.txt中一定不要加-dontoptimize才起作用
# 另外的一种实现方案是通过BuildConfig.DEBUG的变量来控制
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 保持js引擎调用的java类
-keep class * extends io.legado.app.help.JsExtensions{*;}
# 数据类
-keep class **.data.entities.**{*;}
# hutool-core hutool-crypto
-keep class
!cn.hutool.core.util.RuntimeUtil,
!cn.hutool.core.util.ClassLoaderUtil,
!cn.hutool.core.util.ReflectUtil,
!cn.hutool.core.util.SerializeUtil,
!cn.hutool.core.util.ClassUtil,
cn.hutool.core.codec.**,
cn.hutool.core.util.**{*;}
-keep class cn.hutool.crypto.**{*;}
-dontwarn cn.hutool.**
# 缓存 Cookie
-keep class **.help.http.CookieStore{*;}
-keep class **.help.CacheManager{*;}
# StrResponse
-keep class **.help.http.StrResponse{*;}

# markwon
-dontwarn org.commonmark.ext.gfm.**

# okhttp3 — keep internal classes with obfuscation allowed, precise keep for public API
-keep,allowobfuscation class okhttp3.** { *; }
-keep class okhttp3.Headers { public *; }
-keep class okhttp3.Response { public *; }
-keep class okhttp3.Response$Builder { public *; }
-keep class okhttp3.Request { public *; }
-keep class okhttp3.RequestBody { public *; }
-keep class okhttp3.ResponseBody { public *; }
-keep class okhttp3.MediaType { public *; }
-keep class okhttp3.OkHttpClient { public *; }
-keep class okhttp3.OkHttpClient$Builder { public *; }
-keep class okhttp3.Call { public *; }
-keep class okhttp3.Callback { public *; }
-keep class okhttp3.Cookie { public *; }
-keep class okhttp3.HttpUrl { public *; }
-keep class okhttp3.Protocol { public *; }
-keep class okhttp3.CacheControl { public *; }
-keep class okhttp3.Interceptor { public *; }
-keep class okhttp3.ConnectionSpec { public *; }
-keep class okhttp3.Credentials { public *; }
-keep class okhttp3.CookieJar { public *; }
-keep class okhttp3.FormBody { public *; }
-keep class okhttp3.MultipartBody { public *; }

# okio — keep internal classes with obfuscation allowed, precise keep for public API
-keep,allowobfuscation class okio.** { *; }
-keep class okio.Buffer { public *; }
-keep class okio.BufferedSink { public *; }
-keep class okio.BufferedSource { public *; }
-keep class okio.Pipe { public *; }
-keep class okio.Source { public *; }
-keep class okio.Timeout { public *; }

# jayway jsonpath — keep only public API used by the app
-keep,allowobfuscation class com.jayway.jsonpath.** { *; }
-keep class com.jayway.jsonpath.JsonPath { public *; }
-keep class com.jayway.jsonpath.ReadContext { public *; }
-keep class com.jayway.jsonpath.DocumentContext { public *; }
-keep class com.jayway.jsonpath.ParseContext { public *; }
-keep class com.jayway.jsonpath.Configuration { public *; }
-keep class com.jayway.jsonpath.Option { public *; }
# jayway jsonpath optional providers (not included in this project)
-dontwarn com.fasterxml.jackson.**
-dontwarn jakarta.json.**
-dontwarn jakarta.json.bind.**
-dontwarn jakarta.json.spi.**
-dontwarn jakarta.json.stream.**
-dontwarn org.apache.tapestry5.json.**
-dontwarn org.codehaus.jettison.json.**

# LiveEventBus
-keepclassmembers class androidx.lifecycle.LiveData {
    *** mObservers;
    *** mActiveCount;
}
-keepclassmembers class androidx.arch.core.internal.SafeIterableMap {
    *** size();
    *** putIfAbsent(...);
}

## ChangeBookSourceDialog initNavigationView
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    *** mNavButtonView;
}

## ViewExtensions ViewPager.setEdgeEffectColor
-keepclassmembers class androidx.viewpager.widget.ViewPager {
    *** mLeftEdge;
    *** mRightEdge;
}

## ViewExtensions PopupMenu.show
-keepclassmembers class androidx.appcompat.widget.PopupMenu {
    *** mPopup;
}

## TintHelper setCursorTint
-keepclassmembers class android.widget.TextView {
    *** mCursorDrawableRes;
    *** mEditor;
}
-keepclassmembers class android.widget.Editor {
    *** mCursorDrawable;
}

## PreferencesExtensions getSharedPreferences
-keepclassmembers class android.content.ContextWrapper {
    *** mBase;
}
-keepclassmembers class android.app.ContextImpl {
    *** mPreferencesDir;
}

# MenuExtensions applyOpenTint
-keepnames class androidx.appcompat.view.menu.SubMenuBuilder
-keep class androidx.appcompat.view.menu.MenuBuilder {
    *** setOptionalIconsVisible(...);
    *** getNonActionItems();
}

# FileDocExtensions.kt treeDocumentFileConstructor
-keep class androidx.documentfile.provider.TreeDocumentFile {
    <init>(...);
}

# JsoupXpath
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.AxisSelector{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.NodeTest{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.Function{*;}

## JSOUP — keep internal classes with obfuscation allowed, precise keep for public API
-keep,allowobfuscation class org.jsoup.** { *; }
-keep class org.jsoup.Jsoup { public *; }
-keep class org.jsoup.Connection { public *; }
-keep class org.jsoup.nodes.Document { public *; }
-keep class org.jsoup.nodes.Element { public *; }
-keep class org.jsoup.nodes.Node { public *; }
-keep class org.jsoup.nodes.TextNode { public *; }
-keep class org.jsoup.nodes.CDataNode { public *; }
-keep class org.jsoup.select.Elements { public *; }
-keep class org.jsoup.select.Collector { public *; }
-keep class org.jsoup.select.Evaluator { public *; }
-keep class org.jsoup.select.NodeTraversor { public *; }
-keep class org.jsoup.select.NodeVisitor { public *; }
-keep class org.jsoup.parser.Parser { public *; }
-keep class org.jsoup.internal.StringUtil { public *; }
-dontwarn org.jspecify.annotations.NullMarked

## ExoPlayer 反射设置ua 保证该私有变量不被混淆
-keepclassmembers class androidx.media3.datasource.cache.CacheDataSource$Factory {
    *** upstreamDataSourceFactory;
}
## ExoPlayer 如果还不能播放就取消注释这个
# -keep class com.google.android.exoplayer2.** {*;}

## 对外提供api
-keep class io.legado.app.api.ReturnData{*;}

# Throwable
-keepnames class * extends java.lang.Throwable
-keepclassmembernames,allowobfuscation class * extends java.lang.Throwable{*;}
