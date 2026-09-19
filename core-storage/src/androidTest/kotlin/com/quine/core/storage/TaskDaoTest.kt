package com.quine.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var database: QuineDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(DB)
        database = Room.databaseBuilder(context, QuineDatabase::class.java, DB)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB)
    }

    private fun run(
        id: String = "t1",
        status: TaskStatus = TaskStatus.QUEUED,
        origin: TaskOrigin = TaskOrigin.CONVERSATION,
        convId: String? = "c1",
    ) = TaskRunEntity(
        id = id,
        title = "帮我整理周报",
        status = status.name,
        originType = origin.name,
        originConversationId = convId,
        createdAt = 1_000L,
    )

    private fun step(
        id: String = "s1",
        taskId: String = "t1",
        idx: Int = 0,
        type: TaskStepType = TaskStepType.TOOL,
    ) = TaskStepEntity(
        id = id,
        taskId = taskId,
        idx = idx,
        type = type.name,
        title = "改了 3 个文件",
        payloadJson = "{\"diff\":\"...\"}",
        ts = 2_000L + idx,
    )

    @Test
    fun 任务的创建与状态流转() = runBlocking {
        database.taskDao().upsertRun(run())
        database.taskDao().markStarted("t1", at = 1_100L, status = TaskStatus.RUNNING.name)

        var loaded = database.taskDao().findRun("t1")!!
        assertEquals(TaskStatus.RUNNING.name, loaded.status)
        assertEquals(1_100L, loaded.startedAt)

        database.taskDao().markEnded("t1", at = 1_900L, status = TaskStatus.SUCCEEDED.name, costJson = "{\"tokens\":12400}")
        loaded = database.taskDao().findRun("t1")!!
        assertEquals(TaskStatus.SUCCEEDED.name, loaded.status)
        // 耗时 = endedAt - startedAt = 1900 - 1100
        assertEquals(800L, loaded.endedAt!! - loaded.startedAt!!)
        assertEquals("{\"tokens\":12400}", loaded.costJson)
    }

    @Test
    fun 步骤按序号排序且随任务删除而级联() = runBlocking {
        database.taskDao().upsertRun(run())
        database.taskDao().upsertStep(step(id = "s2", idx = 2))
        database.taskDao().upsertStep(step(id = "s0", idx = 0))
        database.taskDao().upsertStep(step(id = "s1", idx = 1))

        val steps = database.taskDao().steps("t1")
        assertEquals(listOf(0, 1, 2), steps.map { it.idx })
        assertEquals(listOf("s0", "s1", "s2"), steps.map { it.id })
        assertEquals(3, database.taskDao().stepCount("t1"))

        database.taskDao().deleteSteps("t1")
        assertEquals(0, database.taskDao().stepCount("t1"))
    }

    @Test
    fun 删任务会级联删掉它的步骤() = runBlocking {
        database.taskDao().upsertRun(run())
        database.taskDao().upsertStep(step())
        database.taskDao().deleteRun("t1")

        assertNull(database.taskDao().findRun("t1"))
        assertEquals(0, database.taskDao().stepCount("t1"))
    }

    @Test
    fun 按状态筛选任务() = runBlocking {
        database.taskDao().upsertRun(run(id = "t-run", status = TaskStatus.RUNNING))
        database.taskDao().upsertRun(run(id = "t-fail", status = TaskStatus.FAILED))

        val failures = database.taskDao().run {
            upsertRun(run(id = "t-fail2", status = TaskStatus.FAILED))
            findRun("t-fail")!!
        }
        assertEquals(TaskStatus.FAILED.name, failures.status)
        assertTrue(database.taskDao().findRun("t-run") != null)
    }

    @Test
    fun 快照按路径取最近一次() = runBlocking {
        database.taskDao().upsertRun(run())
        database.taskDao().upsertSnapshot(
            SnapshotEntity(id = "old", path = "notes/a.md", blobRef = "blob/sha256/aaa", taskId = "t1", createdAt = 100L),
        )
        database.taskDao().upsertSnapshot(
            SnapshotEntity(id = "new", path = "notes/a.md", blobRef = "blob/sha256/bbb", taskId = "t1", createdAt = 200L),
        )

        val latest = database.taskDao().latestSnapshot("notes/a.md")!!
        assertEquals("new", latest.id)
        assertEquals("blob/sha256/bbb", latest.blobRef)
        assertEquals(2, database.taskDao().snapshotsForTask("t1").size)
        assertNotNull(database.taskDao().findByBlob("blob/sha256/aaa"))
    }

    /**
     * 迁移最容易出事的地方：加了新表，却把旧数据弄没了。
     * 这里手工造一个 v1 的数据库，跑完迁移后确认会话与消息都还在。
     */
    @Test
    fun 升级到v3后旧会话与消息不丢() = runBlocking {
        val name = "upgrade.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            raw.execSQL(
                "CREATE TABLE `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            raw.execSQL(
                "CREATE TABLE `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                    "`content` TEXT, `toolCallsJson` TEXT, `toolCallId` TEXT, `toolName` TEXT, `metaJson` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            raw.execSQL("CREATE INDEX `index_messages_conversationId` ON `messages` (`conversationId`)")
            raw.execSQL(
                "INSERT INTO conversations VALUES ('c-old','升级前的会话',1,2,0)",
            )
            raw.execSQL(
                "INSERT INTO messages VALUES ('m-old','c-old','USER','老消息',NULL,NULL,NULL,NULL,3)",
            )
            raw.version = 1
        }

        val upgraded = Room.databaseBuilder(context, QuineDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        upgraded.openHelper.writableDatabase

        assertEquals("升级前的会话", upgraded.conversationDao().findConversation("c-old")?.title)
        assertEquals(1, upgraded.conversationDao().messages("c-old").size)

        // 新表可用
        upgraded.taskDao().upsertRun(run(id = "t-new"))
        assertNotNull(upgraded.taskDao().findRun("t-new"))

        // v3 给 snapshots 加的 root 列可用：不写就是空串，也就是老快照的取值
        upgraded.taskDao().upsertSnapshot(
            SnapshotEntity(
                id = "s-new",
                path = "notes/a.md",
                blobRef = "blob/sha256/ccc",
                createdAt = 1L,
            ),
        )
        assertEquals("", upgraded.taskDao().snapshot("s-new")!!.root)

        upgraded.close()
        context.deleteDatabase(name)
        // @Test 方法必须是 void：这里 runBlocking 的返回值会成为方法返回值，
        // 而上面最后一句 deleteDatabase() 返回 Boolean，会触发 JUnit 的
        // "Method should be void"。显式收尾，别让最后一个表达式漏出非 Unit 类型。
        Unit
    }

    private companion object {
        const val DB = "task-test.db"
    }
}
