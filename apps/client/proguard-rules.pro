# HENGJI uses Room reflection, generated serializers, and enum-backed persistence.
# Preserve application/domain ABI in Beta release builds; dependency shrinking still
# applies, while optimization cannot erase enum shape or generated entry points.
-keep,includedescriptorclasses class com.hengji.** { *; }

# The bundled SQLite driver binds JVM symbols to native functions. Renaming those
# methods changes the JNI signature and makes a release app fail at database open.
-keep class androidx.sqlite.driver.bundled.** { *; }

# cryptography-kotlin discovers its desktop JDK provider through ServiceLoader.
# Compose Desktop ProGuard does not infer these registrations, so keep both
# containers named by the provider's META-INF/services descriptor.
-keep class dev.whyoleg.cryptography.CryptographyProviderContainer
-keep class dev.whyoleg.cryptography.providers.jdk.JdkCryptographyProviderContainer

# The JDK provider contains an optional reflective Bouncy Castle bridge. Hengji
# uses the JDK 21 AES-GCM implementation and does not ship the BC backend.
-dontwarn org.bouncycastle.**

# JNA dispatches JNI entry points and Win32 library methods by their Java names.
# Preserve those names plus the DPAPI structures used by the Windows key vault.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.sun.jna.* { *; }
-keep interface com.sun.jna.* { *; }
-keep interface com.sun.jna.platform.win32.Crypt32 { *; }
-keep interface com.sun.jna.platform.win32.Kernel32 { *; }
-keep class com.sun.jna.platform.win32.WinCrypt$DATA_BLOB { *; }
-keep class com.sun.jna.platform.win32.WinCrypt$CRYPTPROTECT_PROMPTSTRUCT { *; }
