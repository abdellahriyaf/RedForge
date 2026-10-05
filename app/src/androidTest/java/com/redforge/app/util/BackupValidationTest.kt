package com.redforge.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.redforge.app.data.local.db.RedForgeDatabase
import com.redforge.app.data.local.entities.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupValidationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RedForgeDatabase.closeInstance()
        context.deleteDatabase("redforge.db")
    }

    @After
    fun tearDown() {
        RedForgeDatabase.closeInstance()
        context.deleteDatabase("redforge.db")
    }

    @Test
    fun exportAndImportRoundTripPreservesDatabaseData() = runBlocking {
        val database = RedForgeDatabase.getInstance(context)
        val exerciseName = "Backup Round Trip Exercise"

        database.exerciseDao().upsert(
            Exercise(
                name = exerciseName,
                muscleGroup = "Test",
                isCustom = true
            )
        )

        assertNotNull(
            "test exercise was not persisted before backup",
            database.exerciseDao().getByName(exerciseName)
        )

        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray())
            .use { }

        val backupUri = DataBackupUtil.exportBackup(context)
        assertNotNull("backup export returned null", backupUri)

        val exportedUri = backupUri!!
        assertTrue(
            "exported backup failed validation",
            DataBackupUtil.isValidBackup(context, exportedUri)
        )

        RedForgeDatabase.closeInstance()
        assertTrue(
            "test database file could not be deleted before restore",
            context.deleteDatabase("redforge.db")
        )

        assertTrue(
            "backup import returned false",
            DataBackupUtil.importBackup(context, exportedUri)
        )

        RedForgeDatabase.closeInstance()
        val restored = RedForgeDatabase.getInstance(context)

        assertNotNull(
            "restored database did not contain the test exercise",
            restored.exerciseDao().getByName(exerciseName)
        )
    }
}
