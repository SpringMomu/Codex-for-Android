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
    ],
    version = 5,
    exportSchema = false,
)
abstract class CodexDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        fun create(context: Context): CodexDatabase = Room.databaseBuilder(
            context = context,
            klass = CodexDatabase::class.java,
            name = "codex-android.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
