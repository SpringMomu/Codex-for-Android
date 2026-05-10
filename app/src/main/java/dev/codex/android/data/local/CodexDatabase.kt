package dev.codex.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ImageGenerationEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class CodexDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun imageGenerationDao(): ImageGenerationDao

    companion object {
        fun create(context: Context): CodexDatabase = Room.databaseBuilder(
            context = context,
            klass = CodexDatabase::class.java,
            name = "codex-android.db",
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
            .build()
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE messages ADD COLUMN imagePaths TEXT NOT NULL DEFAULT '[]'",
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE messages ADD COLUMN reasoningSummary TEXT NOT NULL DEFAULT ''",
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE messages ADD COLUMN webSearchState TEXT NOT NULL DEFAULT ''",
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE messages ADD COLUMN activityLog TEXT NOT NULL DEFAULT '[]'",
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS image_generations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                prompt TEXT NOT NULL,
                referenceImagePath TEXT,
                generatedImagePath TEXT,
                status TEXT NOT NULL,
                errorMessage TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE image_generations ADD COLUMN referenceImagePaths TEXT NOT NULL DEFAULT '[]'",
        )
    }
}
