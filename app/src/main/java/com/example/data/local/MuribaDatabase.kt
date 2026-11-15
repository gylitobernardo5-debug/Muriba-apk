package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ServerEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MuribaDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE servers ADD COLUMN region TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE servers ADD COLUMN catalogUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE servers ADD COLUMN lastLatencyMs INTEGER")
                db.execSQL("ALTER TABLE servers ADD COLUMN lastSpeedBps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN lastScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_sourceType ON servers(sourceType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_lastScore ON servers(lastScore)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_isFavorite ON servers(isFavorite)")
            }
        }
    }
}
