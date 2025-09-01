# =============================================================================
# 🚀 OPTIMIZED PROGUARD RULES FOR RELEASE BUILD
# =============================================================================
# Generated for: DeepSeekChat4 Android App
# Purpose: Maximum APK size reduction while ensuring functionality
# R8 Compatible: YES
# =============================================================================

# =============================================================================
# 🎯 R8 OPTIMIZATIONS - AGGRESSIVE SIZE REDUCTION
# =============================================================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-overloadaggressively
-mergeinterfacesaggressively

# Enable advanced optimizations
-assumevalues class android.os.Build$VERSION {
    int SDK_INT return 28..35;
}

# Remove debug/logging code in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# =============================================================================
# 📱 CORE ANDROID & ANDROIDX - MINIMAL KEEP RULES
# =============================================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep only essential lifecycle methods
-keepclassmembers class * extends android.app.Activity {
    protected void onCreate(android.os.Bundle);
    protected void onResume();
    protected void onPause();
    public void onActivityResult(int, int, android.content.Intent);
}

-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public void onCreate(android.os.Bundle);
    public android.view.View onCreateView(...);
}

# Keep essential View classes
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# =============================================================================
# 🏗️ HILT DEPENDENCY INJECTION - CRITICAL FOR STARTUP
# =============================================================================
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.Component class * { *; }
-keep @dagger.Module class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}

# =============================================================================
# 🏦 ROOM DATABASE - OPTIMIZED RULES
# =============================================================================
-keep class androidx.room.RoomDatabase { *; }
-keep class androidx.room.Room { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep app database and entities
-keep class com.cyberflux.qwinai.database.AppDatabase { *; }
-keep class com.cyberflux.qwinai.model.ChatMessage { *; }
-keep class com.cyberflux.qwinai.model.Conversation { *; }
-keep class com.cyberflux.qwinai.model.GeneratedImage { *; }
-keep class com.cyberflux.qwinai.dao.* { *; }

# =============================================================================
# 🌐 NETWORKING - RETROFIT & MOSHI - MINIMAL RULES
# =============================================================================
-keep class retrofit2.http.* { *; }
-keepclassmembers,allowobfuscation class * {
    @retrofit2.http.* <methods>;
}

# Moshi JSON serialization
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class * extends com.squareup.moshi.JsonAdapter {
    <init>(...);
}

# Keep API models only (not entire network package)
-keep class com.cyberflux.qwinai.network.AimlApiRequest { *; }
-keep class com.cyberflux.qwinai.network.AimlApiResponse { *; }
-keep class com.cyberflux.qwinai.network.AimlApiService { *; }
-keep class com.cyberflux.qwinai.network.OCRApiService { *; }

# =============================================================================
# 📝 MARKWON MARKDOWN - CRITICAL FIX
# =============================================================================
-keep class io.noties.markwon.Markwon { *; }
-keep class io.noties.markwon.Markwon$Builder { *; }
-keep class io.noties.markwon.MarkwonVisitor { *; }
-keep class io.noties.markwon.AbstractMarkwonPlugin { *; }

# CommonMark core (CRITICAL - prevents crashes)
-keep class org.commonmark.parser.Parser { *; }
-keep class org.commonmark.renderer.html.HtmlRenderer { *; }
-keep class org.commonmark.internal.util.Html5Entities { *; }

# Keep syntax highlighting
-keep class io.noties.prism4j.** { *; }
-keep class com.cyberflux.qwinai.utils.CodeSyntaxHighlighter { *; }
-keep class com.cyberflux.qwinai.utils.UnifiedMarkdownProcessor { *; }

# =============================================================================
# 💰 BILLING SYSTEMS - MULTI-PLATFORM
# =============================================================================
# Google Play Billing
-keep class com.android.billingclient.api.** { *; }

# Huawei HMS IAP - CRITICAL FOR HUAWEI DEVICES
-keep class com.huawei.hms.iap.** { *; }
-keep class com.huawei.hms.support.api.entity.** { *; }
-keep class com.huawei.agconnect.** { *; }

# Huawei HMS Core - Additional missing classes
-keep class com.huawei.hianalytics.** { *; }
-keep class com.huawei.hms.availableupdate.** { *; }
-keep class com.huawei.hms.framework.** { *; }
-keep class com.huawei.hms.utils.** { *; }
-keep class com.huawei.appgallery.** { *; }

# Huawei IAP Full SDK
-keep class com.huawei.hms.iapfull.** { *; }
-keep interface com.huawei.hms.iapfull.** { *; }

# App billing classes
-keep class com.cyberflux.qwinai.billing.BillingManager { *; }
-keep class com.cyberflux.qwinai.billing.BillingProvider { *; }
-keep class com.cyberflux.qwinai.billing.GooglePlayBillingProvider { *; }
-keep class com.cyberflux.qwinai.billing.HuaweiIapProvider { *; }

# =============================================================================
# 📢 ADS - GOOGLE ADMOB ONLY
# =============================================================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.cyberflux.qwinai.ads.AdManager { *; }
-keep class com.cyberflux.qwinai.ads.mediation.* { *; }

# =============================================================================
# 🖼️ COMPOSE UI - OPTIMIZED
# =============================================================================
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# =============================================================================
# 🌟 APP ENTRY POINTS - CRITICAL
# =============================================================================
-keep public class com.cyberflux.qwinai.MyApp { *; }
-keep public class com.cyberflux.qwinai.MainActivity { *; }
-keep public class com.cyberflux.qwinai.StartActivity { *; }

# Essential managers for startup
-keep class com.cyberflux.qwinai.utils.PrefsManager { *; }
-keep class com.cyberflux.qwinai.utils.ThemeManager { *; }
-keep class com.cyberflux.qwinai.utils.UnifiedStreamingManager { *; }

# =============================================================================
# 🔧 COROUTINES & KOTLIN - MINIMAL
# =============================================================================
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keep class **$WhenMappings { <fields>; }
-keep class **$Companion { *; }

# =============================================================================
# 🖼️ GLIDE IMAGE LOADING
# =============================================================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep class com.cyberflux.qwinai.QwinAppGlideModule { *; }

# =============================================================================
# 🔧 WORKER CLASSES
# =============================================================================
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# =============================================================================
# 📦 SERIALIZABLE & PARCELABLE
# =============================================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# =============================================================================
# 🔍 ENUMS
# =============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# =============================================================================
# 🚫 WARNING SUPPRESSIONS
# =============================================================================
-dontwarn org.apache.xerces.**
-dontwarn org.w3c.dom.**
-dontwarn android.telephony.HwTelephonyManager
-dontwarn com.huawei.android.**
-dontwarn com.huawei.system.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.commons.logging.**

# Huawei HMS optional dependencies
-dontwarn com.huawei.appgallery.log.**
-dontwarn com.huawei.hianalytics.**
-dontwarn com.huawei.hms.availableupdate.**
-dontwarn com.huawei.hms.iapfull.**
-dontwarn com.huawei.libcore.io.**
-dontwarn com.huawei.ohos.localability.**

# Apache Commons (optional CSV processing)
-dontwarn org.apache.commons.lang3.**

# Kotlin UUID (experimental API)
-dontwarn kotlin.uuid.**

# =============================================================================
# 🔧 NATIVE METHODS
# =============================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# =============================================================================
# 📄 BUILD CONFIG
# =============================================================================
-keep class **.BuildConfig { *; }
-keep class com.cyberflux.qwinai.BuildConfig { *; }

# =============================================================================
# 🎯 FINAL OPTIMIZATIONS
# =============================================================================
# Remove unused resources
-keep class **.R$* { *; }

# Optimize string concatenation
-optimizations !code/simplification/string

# Remove unused code paths
-assumenosideeffects class java.lang.System {
    public static long currentTimeMillis();
    public static int identityHashCode(java.lang.Object);
    public static java.lang.SecurityManager getSecurityManager();
    public static java.util.Properties getProperties();
    public static java.lang.String getProperty(java.lang.String);
    public static java.lang.String getenv(java.lang.String);
    public static java.lang.String mapLibraryName(java.lang.String);
    public static java.lang.String getProperty(java.lang.String,java.lang.String);
}

# =============================================================================
# 🔒 SECURITY & OBFUSCATION
# =============================================================================
# Rename packages for better obfuscation (only use one to avoid conflicts)
-repackageclasses ''

# Obfuscate class names aggressively  
-keeppackagenames !com.cyberflux.qwinai.**

# =============================================================================
# END OF OPTIMIZED PROGUARD RULES
# =============================================================================