# HENGJI uses Room reflection, generated serializers, and enum-backed persistence.
# Preserve application/domain ABI in Beta release builds; dependency shrinking still
# applies, while optimization cannot erase enum shape or generated entry points.
-keep class com.hengji.** { *; }

# The bundled SQLite driver binds JVM symbols to native functions. Renaming those
# methods changes the JNI signature and makes a release app fail at database open.
-keep class androidx.sqlite.driver.bundled.** { *; }
