############################################
## 🔒 Base Rules for Obfuscation
############################################
-keepattributes *Annotation*
-keepattributes Exceptions, InnerClasses, Signature, SourceFile, LineNumberTable
-ignorewarnings

############################################
## 🏦 ROOM Database
############################################
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keepnames class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** getInstance(...);
}

############################################
## 🌐 Retrofit, Gson, OkHttp and Okio
############################################
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
# Keep model classes
-keep class com.cyberflux.qwinai.model.** { *; }
# Keep all network API classes and their inner classes
-keep class com.cyberflux.qwinai.network.** { *; }
-keep class com.cyberflux.qwinai.network.**$** { *; }
# Keep all data classes for API serialization
-keep class com.cyberflux.qwinai.network.AimlApiRequest { *; }
-keep class com.cyberflux.qwinai.network.AimlApiRequest$** { *; }
-keep class com.cyberflux.qwinai.network.AimlApiResponse { *; }
-keep class com.cyberflux.qwinai.network.AimlApiResponse$** { *; }
-keep class com.cyberflux.qwinai.network.OCRResponse { *; }
-keep class com.cyberflux.qwinai.network.OCRImage { *; }
-keep class com.cyberflux.qwinai.network.DocumentInput { *; }
-keep class com.cyberflux.qwinai.network.DocumentInput$** { *; }
# Keep Gson annotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep all API service interfaces
-keep interface com.cyberflux.qwinai.network.** { *; }

############################################
## 🖼️ Jetpack Compose
############################################
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }
# Keep all Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
# Keep State and MutableState classes for Compose
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.snapshots.** { *; }

############################################
## 🌐 Glide (image loading)
############################################
-keep class com.bumptech.glide.** { *; }
-keep interface com.bumptech.glide.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule { *; }

############################################
## 🧊 Lottie (animations)
############################################
-keep class com.airbnb.lottie.** { *; }

############################################
## 🌪️ Timber (logging)
############################################
-keep class timber.log.Timber { *; }

############################################
## 📲 Ad SDKs (Google, Unity, Facebook)
############################################
# Google AdMob
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.android.gms.ads.** <methods>;
}

# AppLovin SDK (v13.x)
-keep class com.applovin.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }
-keepattributes *Annotation*,InnerClasses
-keepclassmembers class com.applovin.** {
    <init>(...);
}
-keep public class com.google.android.gms.**
-dontwarn com.google.android.gms.**
-keep class com.applovin.impl.** { *; }
-keep class com.applovin.sdk.** { *; }
-keep class com.applovin.mediation.** { *; }
# Note: AppLovin SDK 13.x handles activities automatically, no need to keep specific activity references

# IronSource SDK
-keep class com.ironsource.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep class com.ironsource.** { *; }
-keep class com.ironsource.adapters.** { *; }
-dontwarn com.ironsource.**
-dontwarn com.ironsource.adapters.**
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Huawei Ads Kit
-keep class com.huawei.openalliance.ad.** { *; }
-keep class com.huawei.hms.ads.** { *; }
-keep interface com.huawei.hms.ads.** { *; }

# Generic ad network rules
-keep class * extends java.util.ListResourceBundle {
    protected *** getContents();
}
-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

# Mediation system classes
-keep class com.cyberflux.qwinai.ads.mediation.** { *; }
-keep class com.cyberflux.qwinai.ads.AdConfig { *; }

############################################
## 🧠 Huawei Mobile Services (HMS) - CRITICAL SECTION
############################################
# HMS Core
-keep class com.huawei.hianalytics.** { *; }
-keep class com.huawei.updatesdk.** { *; }
-keep class com.huawei.hms.** { *; }
-keep class com.huawei.agconnect.** { *; }
-keep class com.huawei.hms.update.** { *; }
-keep class com.huawei.hms.appservice.** { *; }
-keep interface com.huawei.hms.api.HuaweiApiClient$* { *; }
-keep interface com.huawei.hms.api.ConnectionResult { *; }
-keep interface com.huawei.hms.support.** { *; }
-keep class com.huawei.hms.api.HuaweiApiAvailability { *; }
-keep class com.huawei.hms.support.api.client.Result { *; }
-keep class com.huawei.hms.support.api.client.PendingResult { *; }

############################################
## 🛒 Huawei In-App Purchase (IAP) - MOST CRITICAL
############################################
# Keep all HMS IAP classes and interfaces - CRITICAL FOR IAP FUNCTIONALITY
-keep class com.huawei.hms.iap.** {*;}
-keep interface com.huawei.hms.iap.** {*;}
-keep class com.huawei.hms.api.entity.** {*;}
-keep class com.huawei.hms.core.** {*;}
# Keep IAP entity classes - CRITICAL FOR PURCHASE PROCESSING
-keep class com.huawei.hms.iap.entity.** {*;}
-keep class com.huawei.hms.iap.Iap {*;}
-keep class com.huawei.hms.iap.IapClient {*;}
-keep class com.huawei.hms.iap.entity.InAppPurchaseData {*;}
-keep class com.huawei.hms.iap.entity.OwnedPurchasesResult {*;}

############################################
## 🛒 Google Play Billing
############################################
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }
-keep class com.android.vending.billing.** { *; }

############################################
## ⚙️ Your App's Billing Components
############################################
# Keep essential billing components of your app
-keep class com.cyberflux.qwinai.billing.** { *; }
-keep class com.cyberflux.qwinai.billing.HuaweiIapProvider { *; }
-keep class com.cyberflux.qwinai.billing.BillingManager { *; }
-keep class com.cyberflux.qwinai.billing.BillingProvider { *; }
-keep class com.cyberflux.qwinai.billing.ProductInfo { *; }
-keep class com.cyberflux.qwinai.billing.GooglePlayBillingProvider { *; }
-keep class com.cyberflux.qwinai.utils.PrefsManager { *; }
-keep class com.cyberflux.qwinai.MyApp { *; }
# For HuaweiIapProvider companion object methods
-keep class com.cyberflux.qwinai.billing.HuaweiIapProvider$Companion {
    *;
}
# For BillingManager instance methods
-keepclassmembers class com.cyberflux.qwinai.billing.BillingManager {
    public boolean processPurchaseResult(int, int, android.content.Intent);
    public void refreshSubscriptionStatus();
    public void launchBillingFlow(android.app.Activity, java.lang.String);
    public void connectToPlayBilling(kotlin.jvm.functions.Function1);
}
# Keep all constants in BillingProvider
-keepclassmembers class com.cyberflux.qwinai.billing.BillingProvider$Companion {
    public static final java.lang.String *;
}
# For MyApp companion object methods
-keepclassmembers class com.cyberflux.qwinai.MyApp$Companion {
    public boolean isHuaweiDevice();
    public boolean isHuaweiDeviceNonBlocking();
    private boolean isHMSCoreAvailable();
}

############################################
## 🔄 Activity Lifecycle and Results
############################################
# Keep the onActivityResult method in all Activities - CRITICAL FOR PAYMENT FLOW
-keepclassmembers class * extends android.app.Activity {
    public void onActivityResult(int, int, android.content.Intent);
}

############################################
## 👷 Workers and Background Tasks
############################################
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(...);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(...);
}
-keep class com.cyberflux.qwinai.workers.CheckSubscriptionWorker { *; }
-keep class com.cyberflux.qwinai.workers.ConversationCleanupWorker { *; }

############################################
## 📱 Android Framework
############################################
-keep class android.content.** { *; }
-keep class android.app.** { *; }
-keep class android.view.** { *; }
-keep class android.widget.** { *; }
-keep class android.os.** { *; }
-keep class android.graphics.** { *; }

############################################
## 🧰 AndroidX Core Libraries
############################################
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.fragment.** { *; }

############################################
## 🌐 JSON and Data Serialization
############################################
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class org.json.** { *; }
# Keep all Kotlin data classes
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}
# Keep Kotlin reflection for data classes
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keepclassmembers class * {
    synthetic <methods>;
}
# Keep all data class constructors, getters, setters
-keepclassmembers class * {
    public <init>(...);
}
# Keep companion objects
-keep class **$Companion { *; }

############################################
## 🔒 Security
############################################
-keepclassmembers class * {
    @android.annotation.SuppressLint <methods>;
}

# Keep prism4j classes
-keep class io.noties.prism4j.** { *; }

############################################
## 🎬 App Entry Points
############################################
-keep public class com.cyberflux.qwinai.MainActivity
-keep public class com.cyberflux.qwinai.WelcomeActivity
-keep public class com.cyberflux.qwinai.StartActivity
-keep public class com.cyberflux.qwinai.AudioAiActivity
-keep public class com.cyberflux.qwinai.ImageGenerationActivity
-keep public class com.cyberflux.qwinai.FileGenerationActivity
-keep public class com.cyberflux.qwinai.SettingsActivity
-keep public class com.cyberflux.qwinai.BlockingUpdateActivity
-keep public class com.cyberflux.qwinai.TextSelectionActivity
-keep public class com.cyberflux.qwinai.utils.OCRCameraActivity
-keep public class * extends android.app.Application
# Keep all Activities and their lifecycle methods
-keep class * extends android.app.Activity {
    public <methods>;
    public <init>(...);
}
# Keep all Services
-keep class * extends android.app.Service {
    public <methods>;
    public <init>(...);
}

############################################
## 📦 Miscellaneous
############################################
# Prevent eliminating the BuildConfig class
-keep class **.BuildConfig { *; }
# Keep your model classes for Huawei IAP compatibility
-keep class com.cyberflux.qwinai.model.** { *; }
-keep class com.cyberflux.qwinai.network.** { *; }
# Keep any native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# Keep all public methods in billing providers
-keepclassmembers class com.cyberflux.qwinai.billing.* {
    public *;
}
# Keep Companion objects for Huawei classes
-keep class com.cyberflux.qwinai.billing.HuaweiIapProvider$Companion {
    *;
}

############################################
## 📱 Additional Android Framework Protection
############################################
# Keep all ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
# Keep all Fragment classes
-keep class * extends androidx.fragment.app.Fragment {
    <init>(...);
    public <methods>;
}
# Keep all custom View classes
-keep class * extends android.view.View {
    <init>(...);
    public <methods>;
}
# Keep onClick methods referenced in XML
-keepclassmembers class * {
    public void *Click(android.view.View);
    public void *Click();
}
# Keep all intent filters and broadcast receivers
-keep class * extends android.content.BroadcastReceiver {
    public <methods>;
}
# Keep all content providers
-keep class * extends android.content.ContentProvider {
    public <methods>;
}

############################################
## 🎯 App-Specific Critical Classes
############################################
# Keep all utils classes
-keep class com.cyberflux.qwinai.utils.** { *; }
# Keep all service classes  
-keep class com.cyberflux.qwinai.services.** { *; }
# Keep API config
-keep class com.cyberflux.qwinai.ApiConfig { *; }
# Keep all theme and UI classes
-keep class com.cyberflux.qwinai.ui.** { *; }
-keep class com.cyberflux.qwinai.theme.** { *; }
# Keep all adapter classes
-keep class com.cyberflux.qwinai.adapters.** { *; }
# Keep all fragments
-keep class com.cyberflux.qwinai.fragments.** { *; }
# Keep all database classes
-keep class com.cyberflux.qwinai.data.** { *; }
-keep class com.cyberflux.qwinai.database.** { *; }
# Keep all repository classes
-keep class com.cyberflux.qwinai.repository.** { *; }
# Keep all viewmodel classes
-keep class com.cyberflux.qwinai.viewmodel.** { *; }

############################################
## 🔧 Coroutines and Async
############################################
# Keep all coroutines
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.android.** { *; }
# Keep all suspend function metadata
-keepclassmembers class * {
    @kotlin.coroutines.jvm.internal.DebugMetadata <methods>;
}
# Keep Flow classes
-keep class kotlinx.coroutines.flow.** { *; }

############################################
## 🔧 MainActivity and Activity Lifecycle CRITICAL
############################################
# Keep MainActivity and all its methods - CRITICAL FOR PREVENTING CRASHES
-keep class com.cyberflux.qwinai.MainActivity { *; }
-keepclassmembers class com.cyberflux.qwinai.MainActivity {
    *;
}
# FIXED: Keep StartActivity which is the launcher activity
-keep class com.cyberflux.qwinai.StartActivity { *; }
-keepclassmembers class com.cyberflux.qwinai.StartActivity {
    *;
}
# Keep all activity lifecycle methods
-keepclassmembers class * extends android.app.Activity {
    public void onCreate(android.os.Bundle);
    public void onStart();
    public void onResume();
    public void onPause();
    public void onStop();
    public void onDestroy();
    public void onConfigurationChanged(android.content.res.Configuration);
    public void onNewIntent(android.content.Intent);
}
# Keep all fragment lifecycle methods
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public void onCreate(android.os.Bundle);
    public void onStart();
    public void onResume();
    public void onPause();
    public void onStop();
    public void onDestroy();
    public void onDestroyView();
    public void onDetach();
}

############################################
## 🔧 Memory Management and Cleanup
############################################
# Keep all cleanup methods
-keepclassmembers class * {
    public void cleanup();
    public void release();
    public void destroy();
    public void clear();
    public void clearData();
    public void onDestroy();
}
# Keep all initialization methods
-keepclassmembers class * {
    public void initialize();
    public void init();
    public void setup();
    public void start();
    public void stop();
}

############################################
## 🔧 Lateinit and Nullable Safety
############################################
# Keep all lateinit property setters/getters
-keepclassmembers class * {
    public ** get*(...);
    public void set*(...);
}
# Keep all nullable checks
-keep class kotlin.jvm.internal.Intrinsics { *; }
-keep class kotlin.jvm.internal.Reflection { *; }

############################################
## 🔧 Handler and Looper
############################################
# Keep Handler and Looper classes
-keep class android.os.Handler { *; }
-keep class android.os.Looper { *; }
-keep class android.os.Message { *; }
-keep class android.os.MessageQueue { *; }

############################################
## 🔧 Animation and UI
############################################
# Keep animation classes
-keep class android.animation.** { *; }
-keep class android.view.animation.** { *; }
-keep class android.transition.** { *; }

############################################
## 🔧 Speech and Audio
############################################
# Keep speech recognition classes
-keep class android.speech.** { *; }
-keep class android.media.** { *; }

############################################
## 🔧 Additional Critical Classes
############################################
# Keep all ViewModels and LiveData
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
    public *;
}
-keep class androidx.lifecycle.LiveData { *; }
-keep class androidx.lifecycle.MutableLiveData { *; }

# Keep all error handling
-keepclassmembers class * {
    public void showError(java.lang.String);
    public void handleError(java.lang.Exception);
}

# Keep all permission handlers
-keepclassmembers class * {
    public void onRequestPermissionsResult(int, java.lang.String[], int[]);
}

############################################
## 📄 Document Processing Optimizations
############################################
# Remove heavy Apache POI classes for size optimization
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn schemaorg_apache_xmlbeans.**
# More specific rules to avoid affecting java.lang.Object
-assumenosideeffects class org.apache.poi.** {
    public <methods>;
    private <methods>;
    protected <methods>;
}
-assumenosideeffects class org.apache.xmlbeans.** {
    public <methods>;
    private <methods>;
    protected <methods>;
}

# Remove PDFBox classes that cause NumberFormatException on some devices
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**
-assumenosideeffects class com.tom_roush.pdfbox.** { *; }
-assumenosideeffects class org.apache.pdfbox.** { *; }

############################################
## 🚀 Performance Optimizations
############################################
# FIXED: Less aggressive shrinking to prevent crashes
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
# Removed aggressive flags that can cause crashes
# -allowaccessmodification
# -overloadaggressively

# FIXED: Keep error logs for crash debugging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# FIXED: Keep error logs for crash debugging in release
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove unused ML Kit classes
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.vision.**
-assumenosideeffects class com.google.mlkit.** { *; }

# Handle missing device-specific classes
-dontwarn android.telephony.HwTelephonyManager
-dontwarn com.huawei.android.app.PackageManagerEx
-dontwarn com.huawei.android.content.pm.**
-dontwarn com.huawei.android.os.**
-dontwarn com.huawei.system.**

############################################
## 📱 Memory and Performance Optimizations  
############################################
# Optimize animations and UI
-keep class com.cyberflux.qwinai.views.LiquidMorphingView { *; }
-keepclassmembers class com.cyberflux.qwinai.views.** {
    public void onAttachedToWindow();
    public void onDetachedFromWindow();
    public void onVisibilityChanged(...);
}

# Optimize RecyclerView adapters
-keep class com.cyberflux.qwinai.adapter.ChatAdapterOptimizations { *; }
-keep class com.cyberflux.qwinai.adapter.ChatAdapter$Companion { *; }

# Keep ViewHolder types for recycling optimization
-keepclassmembers class com.cyberflux.qwinai.adapter.ChatAdapter {
    public static final int VIEW_TYPE_*;
}