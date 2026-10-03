package com.fort.messenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserAccountEntity::class,
        PersonaCardEntity::class,
        PeerConnectionEntity::class,
        KnockFirstRequestEntity::class,
        ContactPassEntity::class,
        PrivateRoomEntity::class,
        ChatMessageEntity::class,
        MoodRingEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(FortTypeConverters::class)
abstract class FortDatabase : RoomDatabase() {

    abstract fun userAccountDao(): UserAccountDao
    abstract fun personaCardDao(): PersonaCardDao
    abstract fun peerConnectionDao(): PeerConnectionDao
    abstract fun knockFirstDao(): KnockFirstDao
    abstract fun contactPassDao(): ContactPassDao
    abstract fun privateRoomDao(): PrivateRoomDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun moodRingDao(): MoodRingDao

    companion object {
        @Volatile
        private var INSTANCE: FortDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // UserAccount enhancements
                db.execSQL("ALTER TABLE user_account ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_account ADD COLUMN phoneNumber TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE user_account ADD COLUMN showOnlinePresence INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_account ADD COLUMN showTypingIndicator INTEGER NOT NULL DEFAULT 1")

                // PeerConnection enhancements
                db.execSQL("ALTER TABLE peer_connections ADD COLUMN isTyping INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE peer_connections ADD COLUMN lastSeenTimestamp INTEGER NOT NULL DEFAULT 0")

                // ContactPass enhancements
                db.execSQL("ALTER TABLE contact_passes ADD COLUMN claimantUserId TEXT DEFAULT NULL")

                // PrivateRoom enhancements
                db.execSQL("ALTER TABLE private_rooms ADD COLUMN adminIdsJson TEXT NOT NULL DEFAULT '[]'")

                // ChatMessage enhancements
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN senderSignature TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN encryptedLocalPayload TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyToMessageId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyToSenderName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyToText TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN reactionsJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentSize INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): FortDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FortDatabase::class.java,
                    "fort_sovereign_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): FortDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                FortDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
