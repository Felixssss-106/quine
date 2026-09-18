package com.quine.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * M0 只落会话与消息。`exportSchema = false`：M0 不产出 schema 快照，
 * 迁移策略在第一次真正改表结构（M1 的任务表）时再建立。
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class QuineDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    companion object {
        private const val NAME = "quine.db"

        fun create(context: Context): QuineDatabase = Room
            .databaseBuilder(context.applicationContext, QuineDatabase::class.java, NAME)
            .build()
    }
}
