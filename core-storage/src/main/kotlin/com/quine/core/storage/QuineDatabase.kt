package com.quine.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * M0：会话与消息。M1：任务与快照（[MIGRATION_1_2]）。
 *
 * `exportSchema = false`：不产出 schema 快照，迁移靠手写 SQL + 仪器测试兜底
 * （见 `TaskDaoTest` 里的「升级后旧会话不丢」用例）。
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        TaskRunEntity::class,
        TaskStepEntity::class,
        SnapshotEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class QuineDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    abstract fun taskDao(): TaskDao

    companion object {
        private const val NAME = "quine.db"

        fun create(context: Context): QuineDatabase = Room
            .databaseBuilder(context.applicationContext, QuineDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}

/**
 * v1 → v2：只新增三张表，不动 `conversations` / `messages`，所以旧会话不会丢。
 *
 * 索引名沿用 Room 的命名约定（`index_<表>_<列>`），否则 Room 校验 schema 时会判不一致。
 */
internal val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_runs` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `originType` TEXT NOT NULL,
                `originConversationId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `endedAt` INTEGER,
                `costJson` TEXT,
                `errorJson` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_runs_status` ON `task_runs` (`status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_runs_originConversationId` " +
                "ON `task_runs` (`originConversationId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_runs_createdAt` ON `task_runs` (`createdAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_steps` (
                `id` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `idx` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `payloadJson` TEXT,
                `ts` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`taskId`) REFERENCES `task_runs`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_steps_taskId` ON `task_steps` (`taskId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_steps_taskId_idx` ON `task_steps` (`taskId`, `idx`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `snapshots` (
                `id` TEXT NOT NULL,
                `path` TEXT NOT NULL,
                `blobRef` TEXT NOT NULL,
                `taskId` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snapshots_taskId` ON `snapshots` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snapshots_path` ON `snapshots` (`path`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snapshots_createdAt` ON `snapshots` (`createdAt`)")
    }
}
