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

# Keep the generated locator entry point; R8 can still inline and shrink the grammar implementations it references.
-keep class dev.codex.android.ui.markdown.CodexGrammarLocator { *; }
