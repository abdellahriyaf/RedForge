package com.redforge.app.util

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupValidationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun validBackupEnvelopeIsAccepted() {
        val uri = writeArchive(
            listOf(
                "redforge_backup_marker.txt" to
                    "RedForge backup|format=1|dbVersion=5|created=test",
                "redforge.db" to "not a real database"
            )
        )

        assertTrue(DataBackupUtil.isValidBackup(context, uri))
    }

    @Test
    fun duplicateEntriesAreRejected() {
        val file = File(context.cacheDir, "duplicate_backup.zip")
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
    fun pathTraversalEntriesAreRejected() {
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
        val file = File.createTempFile("redforge-backup-test-", ".zip", context.cacheDir)
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
