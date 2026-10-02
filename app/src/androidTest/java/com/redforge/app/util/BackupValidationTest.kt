package com.redforge.app.util

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.redforge.app.data.local.db.RedForgeDatabase
import com.redforge.app.data.local.entities.Exercise
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
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
    fun validBackupEnvelopeIsAccepted() = runBlocking {
        val uri = DataBackupUtil.exportBackup(context)

        assertTrue(uri != null)
        assertTrue(DataBackupUtil.isValidBackup(context, uri!!))
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

    @Test
    fun duplicateEntriesAreRejected() = runBlocking {
        val file = File(context.cacheDir, "share/duplicate_backup.zip").apply {
            parentFile?.mkdirs()
        }
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("redforge_backup_marker.txt"))
            zip.write("RedForge backup|format=1|dbVersion=5|created=test".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("redforge.db"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("redforge.db"))
            zip.write(byteArrayOf(2))
            zip.closeEntry()
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        assertFalse(DataBackupUtil.isValidBackup(context, uri))
        file.delete()
    }

    @Test
    fun pathTraversalEntriesAreRejected() = runBlocking {
        val uri = writeArchive(
            listOf(
                "redforge_backup_marker.txt" to
                    "RedForge backup|format=1|dbVersion=5|created=test",
                "../redforge.db" to "unsafe"
            )
        )

        assertFalse(DataBackupUtil.isValidBackup(context, uri))
    }

    private fun writeArchive(entries: List<Pair<String, String>>): android.net.Uri {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File.createTempFile("redforge-backup-test-", ".zip", directory)
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
