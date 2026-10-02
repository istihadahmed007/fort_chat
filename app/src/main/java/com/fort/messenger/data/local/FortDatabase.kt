package com.fort.messenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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

        fun getInstance(context: Context): FortDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FortDatabase::class.java,
                    "fort_sovereign_database.db"
                )
                    .fallbackToDestructiveMigration()
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
