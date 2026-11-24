package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NodeConfigEntity::class,
        MeshPeerEntity::class,
        MemberEntity::class,
        ChoreEntity::class,
        CompletionEntity::class,
        SavedPlaceEntity::class,
        ReminderEntity::class,
        CalendarEventEntity::class,
        DinnerOptionEntity::class,
        GroceryItemEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class HomeListDatabase : RoomDatabase() {
    abstract fun dao(): HomeListDao

    companion object {
        @Volatile
        private var instance: HomeListDatabase? = null

        /**
         * Safe non-destructive migration preserving paired households, chores, meals, and shopping lists
         * across app updates.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE node_config ADD COLUMN updateReleaseUrl TEXT NOT NULL DEFAULT 'https://github.com/bobcatchris15-eng/HomeList/releases/latest'"
                )
                db.execSQL(
                    "ALTER TABLE mesh_peers ADD COLUMN appVersionCode INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE mesh_peers ADD COLUMN appVersionName TEXT NOT NULL DEFAULT '1.0'"
                )
            }
        }

        fun getInstance(context: Context): HomeListDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeListDatabase::class.java,
                    "homelist_mesh.db"
                ).addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
