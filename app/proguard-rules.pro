# Project-specific ProGuard rules.

# Keep Room database model constructors and generated implementation entry points stable for release builds.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class dev.codex.android.data.local.** { *; }
-keep @androidx.room.Dao class dev.codex.android.data.local.** { *; }

# kotlinx.serialization generates serializers at compile time; keep serializer companions used by reflective fallbacks.
-keepclassmembers class dev.codex.android.** {
    public static ** Companion;
}
-keep class dev.codex.android.**$$serializer { *; }
-keepclassmembers class dev.codex.android.** {
    *** write$Self(...);
}

# Prism4j grammar locator is annotation-generated and discovered by Markwon syntax highlighting.
-keep class dev.codex.android.ui.markdown.CodexGrammarLocator { *; }
-keep class io.noties.prism4j.** { *; }
