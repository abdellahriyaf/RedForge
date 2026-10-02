package com.redforge.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.redforge.app.data.local.db.RedForgeDatabase
import com.redforge.app.data.local.entities.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.After
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
        database.exerciseDao().upsert(
            Exercise(
                name = "Backup Round Trip Exercise",
                muscleGroup = "Test",
                isCustom = true
            )
        )

        val backupUri = DataBackupUtil.exportBackup(context)
        assertTrue(backupUri != null)
        assertTrue(DataBackupUtil.isValidBackup(context, backupUri!!))
        assertTrue(DataBackupUtil.importBackup(context, backupUri))

        RedForgeDatabase.closeInstance()
        val restored = RedForgeDatabase.getInstance(context)
        assertTrue(
            restored.exerciseDao().getByName("Backup Round Trip Exercise") != null
        )
    }
}
