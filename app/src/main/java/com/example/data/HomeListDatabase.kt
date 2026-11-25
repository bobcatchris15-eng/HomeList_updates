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
    version = 7,
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
                    "ALTER TABLE node_config ADD COLUMN updateReleaseUrl TEXT NOT NULL DEFAULT 'https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest'"
                )
                db.execSQL(
                    "ALTER TABLE mesh_peers ADD COLUMN appVersionCode INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE mesh_peers ADD COLUMN appVersionName TEXT NOT NULL DEFAULT '1.0'"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `node_config_new` (
                        `singletonId` INTEGER NOT NULL,
                        `nodeId` TEXT NOT NULL,
                        `householdId` TEXT NOT NULL,
                        `householdName` TEXT NOT NULL,
                        `pairingCode` TEXT NOT NULL,
                        `selfMemberId` TEXT NOT NULL,
                        `selfRole` TEXT NOT NULL,
                        `deviceType` TEXT NOT NULL,
                        `isFounder` INTEGER NOT NULL,
                        `listenPort` INTEGER NOT NULL,
                        `googleCalendarId` INTEGER NOT NULL,
                        `googleCalendarName` TEXT NOT NULL,
                        `icsFeedUrl` TEXT NOT NULL,
                        `updateReleaseUrl` TEXT NOT NULL,
                        `darkTheme` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`singletonId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `node_config_new` (
                        singletonId, nodeId, householdId, householdName, pairingCode,
                        selfMemberId, selfRole, deviceType, isFounder, listenPort,
                        googleCalendarId, googleCalendarName, icsFeedUrl,
                        updateReleaseUrl, darkTheme, lastSyncedAt
                    )
                    SELECT
                        singletonId, nodeId, householdId, householdName, pairingCode,
                        selfMemberId, selfRole, deviceType, isFounder, listenPort,
                        googleCalendarId, googleCalendarName, icsFeedUrl,
                        CASE
                            WHEN updateReleaseUrl = 'https://github.com/bobcatchris15-eng/HomeList/releases/latest'
                              OR updateReleaseUrl = 'https://github.com/bobcatchris15-eng/HomeList'
                            THEN 'https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest'
                            ELSE updateReleaseUrl
                        END,
                        darkTheme, lastSyncedAt
                    FROM `node_config`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `node_config`")
                db.execSQL("ALTER TABLE `node_config_new` RENAME TO `node_config`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mesh_peers_new` (
                        `nodeId` TEXT NOT NULL,
                        `memberId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `deviceType` TEXT NOT NULL,
                        `isFounder` INTEGER NOT NULL,
                        `lanIp` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `appVersionCode` INTEGER NOT NULL,
                        `appVersionName` TEXT NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`nodeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `mesh_peers_new` (
                        nodeId, memberId, displayName, role, deviceType,
                        isFounder, lanIp, port, appVersionCode, appVersionName,
                        lastSeenAt, updatedAt
                    )
                    SELECT
                        nodeId, memberId, displayName, role, deviceType,
                        isFounder, lanIp, port, appVersionCode, appVersionName,
                        lastSeenAt, updatedAt
                    FROM `mesh_peers`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `mesh_peers`")
                db.execSQL("ALTER TABLE `mesh_peers_new` RENAME TO `mesh_peers`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chores ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE chores ADD COLUMN dueDateKey TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE node_config ADD COLUMN hubUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): HomeListDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeListDatabase::class.java,
                    "homelist_mesh.db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
