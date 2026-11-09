package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        EmailEntity::class,
        ReminderEntity::class,
        NotificationHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MailMindDatabase : RoomDatabase() {
    abstract fun mailMindDao(): MailMindDao

    companion object {
        @Volatile
        private var INSTANCE: MailMindDatabase? = null

        fun getDatabase(context: Context): MailMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MailMindDatabase::class.java,
                    "mailmind_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
