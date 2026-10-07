package com.nathzramirez.thesisflow.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * pending_uploads holds files that exist nowhere else yet, so a schema change
 * must never drop them. These tests run every auto-migration against the
 * exported schemas in data/schemas, checking each step's result against the
 * schema Room expects, and prove a queued upload survives the whole chain.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ThesisFlowDatabase::class.java,
    )

    @Test
    fun aQueuedUploadSurvivesEveryMigration() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO pending_uploads (fileId, groupId, chapterId, kind, cachedPath, fileName, mimeType,
                    sizeBytes, note, uploadedBy, state, progressPercent, failure, createdAt)
                VALUES ('file-1', 'group-1', 'ch-1', 'DRAFT', '/data/pending/file-1/Chapter1.pdf', 'Chapter1.pdf',
                    'application/pdf', 250000, 'Fixed citations', 'ben', 'QUEUED', 0, NULL, 1790000000000)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, LATEST, true).use { db ->
            db.query("SELECT fileName, note, state, taskId, feedbackId FROM pending_uploads WHERE fileId = 'file-1'")
                .use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals("Chapter1.pdf", cursor.getString(0))
                    assertEquals("Fixed citations", cursor.getString(1))
                    assertEquals("QUEUED", cursor.getString(2))
                    // Columns added later start empty for rows that existed before them.
                    assertNull(cursor.getString(3))
                    assertNull(cursor.getString(4))
                }
        }
    }

    @Test
    fun theFirstSchemaUpgradesToTheLatest() {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, LATEST, true).close()
    }

    private companion object {
        const val DB = "migration-test.db"
        const val LATEST = 5
    }
}
