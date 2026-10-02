package com.redforge.app.util

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.FileProvider
import com.redforge.app.data.local.db.RedForgeDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manual, user-initiated local backup/restore.
 *
 * The backup contains the Room database, DataStore preferences and progress
 * photos. Nothing is uploaded automatically by RedForge.
 */
object DataBackupUtil {

    private const val BACKUP_FORMAT_VERSION = 1
    private const val DATABASE_VERSION = 5
    private const val DB_ENTRY = "redforge.db"
    private const val PREFS_ENTRY = "redforge_settings.preferences_pb"
    private const val PHOTOS_ENTRY_PREFIX = "progress_photos/"
    private const val MARKER_ENTRY = "redforge_backup_marker.txt"

    private const val MAX_ENTRY_BYTES = 100L * 1024L * 1024L
    private const val MAX_BACKUP_UNCOMPRESSED_BYTES = 250L * 1024L * 1024L

    suspend fun exportBackup(context: Context): Uri? {
        var zipFile: File? = null
        return try {
            val db = RedForgeDatabase.getInstance(context)
            RedForgeDatabase.awaitSeeded()

            if (!db.isOpen) {
                return null
            }

            // Flush WAL contents through the same Room database handle that
            // owns the current connection pool. Opening a second SQLite handle
            // can leave Room's most recent writes in its WAL while the main file
            // is copied below, producing a valid but stale backup.
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                    throw IllegalStateException(
                        "Could not checkpoint the database before backup"
                    )
                }
            }

            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

            val exportDir = File(
                context.cacheDir,
                "share"
            ).apply {
                mkdirs()
            }

            zipFile = File(
                exportDir,
                "redforge_backup_$timestamp.zip"
            )

            val dbFile = context.getDatabasePath("redforge.db")
            if (!dbFile.isFile) {
                return null
            }

            val snapshotFile = File.createTempFile(
                "redforge_db_snapshot_",
                ".db",
                context.cacheDir
            )

            try {
                copyFile(dbFile, snapshotFile)

                validateSQLiteDatabase(snapshotFile)

                ZipOutputStream(
                    FileOutputStream(requireNotNull(zipFile))
                ).use { zip ->
                    writeTextEntry(
                        zip,
                        MARKER_ENTRY,
                        "RedForge backup|format=$BACKUP_FORMAT_VERSION|dbVersion=$DATABASE_VERSION|created=$timestamp"
                    )

                    addFileToZip(
                        zip,
                        snapshotFile,
                        DB_ENTRY
                    )

                    val prefsFile = File(
                        context.filesDir.parentFile,
                        "datastore/redforge_settings.preferences_pb"
                    )

                    if (prefsFile.isFile) {
                        addFileToZip(
                            zip,
                            prefsFile,
                            PREFS_ENTRY
                        )
                    }

                    val photosDir = File(
                        context.filesDir,
                        "progress_photos"
                    )

                    if (photosDir.isDirectory) {
                        photosDir
                            .listFiles()
                            ?.filter { it.isFile }
                            ?.forEach { photo ->
                                val safeName = photo.name
                                    .replace("/", "_")
                                    .replace("\\", "_")

                                addFileToZip(
                                    zip,
                                    photo,
                                    PHOTOS_ENTRY_PREFIX + safeName
                                )
                            }
                    }
                }
            } finally {
                snapshotFile.delete()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                requireNotNull(zipFile)
            )
        } catch (e: Exception) {
            runCatching { zipFile?.delete() }
            throw e
        }
    }

    fun shareBackup(
        context: Context,
        uri: Uri
    ) {
        val intent = Intent(
            Intent.ACTION_SEND
        ).apply {
            type = "application/zip"
            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                "Save your RedForge backup"
            )
        )
    }

    fun isValidBackup(
        context: Context,
        uri: Uri
    ): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var total = 0L
                    var markerFound = false
                    var databaseFound = false
                    val seenEntries = HashSet<String>()
                    var entry = zip.nextEntry

                    while (entry != null) {
                        validateEntryName(
                            entry.name,
                            allowMarker = true
                        )

                        if (!seenEntries.add(entry.name)) {
                            throw IllegalArgumentException("Duplicate archive entry")
                        }


                        when {
                            entry.name == MARKER_ENTRY -> {
                                val markerBytes = readEntryLimited(
                                    zip,
                                    MAX_ENTRY_BYTES
                                )

                                val marker = markerBytes.toString(
                                    Charsets.UTF_8
                                )

                                if (!isSupportedMarker(marker)) {
                                    return@use false
                                }

                                markerFound = true
                                total += markerBytes.size.toLong()
                            }

                            entry.name == DB_ENTRY -> {
                                val bytes = skipEntry(zip)
                                total += bytes
                                databaseFound = true
                            }

                            else -> {
                                total += skipEntry(zip)
                            }
                        }

                        if (total > MAX_BACKUP_UNCOMPRESSED_BYTES) {
                            return@use false
                        }

                        entry = zip.nextEntry
                    }

                    if (!markerFound || !databaseFound) {
                        return@use false
                    }

                    true
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Restores into a staging directory first. Only after all entries and the
     * SQLite file are validated does the method replace the live files.
     */
    fun importBackup(
        context: Context,
        uri: Uri
    ): Boolean {
        val stagingRoot = File(
            context.cacheDir,
            "restore_${UUID.randomUUID()}"
        )
        val stagingPhotos = File(
            stagingRoot,
            PHOTOS_ENTRY_PREFIX
        )
        val stagedDb = File(
            stagingRoot,
            DB_ENTRY
        )
        val stagedPrefs = File(
            stagingRoot,
            PREFS_ENTRY
        )

        val currentDb = context.getDatabasePath(
            "redforge.db"
        )
        val currentPrefs = File(
            context.filesDir.parentFile,
            "datastore/redforge_settings.preferences_pb"
        )
        val currentPhotos = File(
            context.filesDir,
            "progress_photos"
        )

        val rollbackRoot = File(
            context.cacheDir,
            "rollback_${UUID.randomUUID()}"
        )
        val rollbackDb = File(
            rollbackRoot,
            DB_ENTRY
        )
        val rollbackPrefs = File(
            rollbackRoot,
            PREFS_ENTRY
        )
        val rollbackPhotos = File(
            rollbackRoot,
            PHOTOS_ENTRY_PREFIX
        )
        val hadCurrentDb = currentDb.isFile
        val hadCurrentPrefs = currentPrefs.isFile
        val hadCurrentPhotos = currentPhotos.isDirectory

        return try {
            stagingRoot.mkdirs()
            stagingPhotos.mkdirs()

            var total = 0L
            var markerFound = false
            var databaseFound = false
            var preferencesFound = false
            val seenEntries = HashSet<String>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry

                    while (entry != null) {
                        validateEntryName(
                            entry.name,
                            allowMarker = true
                        )

                        if (!seenEntries.add(entry.name)) {
                            throw IllegalArgumentException("Duplicate archive entry")
                        }

                        when {
                            entry.name == MARKER_ENTRY -> {
                                val markerBytes = readEntryLimited(
                                    zip,
                                    MAX_ENTRY_BYTES
                                )
                                val marker = markerBytes.toString(
                                    Charsets.UTF_8
                                )

                                if (!isSupportedMarker(marker)) {
                                    throw IllegalArgumentException(
                                        "Unsupported backup format"
                                    )
                                }

                                markerFound = true
                                total += markerBytes.size.toLong()
                            }

                            entry.name == DB_ENTRY -> {
                                total += writeEntryLimited(
                                    zip,
                                    stagedDb
                                )
                                databaseFound = true
                            }

                            entry.name == PREFS_ENTRY -> {
                                total += writeEntryLimited(
                                    zip,
                                    stagedPrefs
                                )
                                preferencesFound = true
                            }

                            entry.name.startsWith(
                                PHOTOS_ENTRY_PREFIX
                            ) && !entry.isDirectory -> {
                                val fileName = entry.name.removePrefix(
                                    PHOTOS_ENTRY_PREFIX
                                )

                                if (
                                    fileName.isBlank() ||
                                    fileName.contains("/") ||
                                    fileName.contains("\\")
                                ) {
                                    throw IllegalArgumentException(
                                        "Unsafe photo path"
                                    )
                                }

                                total += writeEntryLimited(
                                    zip,
                                    File(
                                        stagingPhotos,
                                        fileName
                                    )
                                )
                            }

                            else -> {
                                skipEntry(zip)
                            }
                        }

                        if (total > MAX_BACKUP_UNCOMPRESSED_BYTES) {
                            throw IllegalArgumentException(
                                "Backup too large"
                            )
                        }

                        entry = zip.nextEntry
                    }
                }
            } ?: return false

            if (!markerFound || !databaseFound || !stagedDb.isFile) {
                return false
            }

            validateSQLiteDatabase(stagedDb)

            // The restore happens only after every archive entry has been
            // extracted and validated.
            RedForgeDatabase.closeInstance()

            // The Room instance is closed before any SQLite file is removed.
            // This is important because the app intentionally restarts after
            // a successful restore so its repositories pick up the new DB.
            rollbackRoot.mkdirs()

            if (currentDb.isFile) {
                copyFile(currentDb, rollbackDb)
            }

            if (currentPrefs.isFile) {
                copyFile(currentPrefs, rollbackPrefs)
            }

            if (currentPhotos.isDirectory) {
                copyDirectory(
                    currentPhotos,
                    rollbackPhotos
                )
            }

            // Remove SQLite sidecars before replacing the database.
            File(currentDb.path + "-wal").delete()
            File(currentDb.path + "-shm").delete()

            // Use Context.deleteDatabase() so Android removes the database
            // together with any journal/WAL sidecars that may still exist.
            // Directly deleting only the main file can leave SQLite state
            // behind and make the subsequent restore fail on emulators.
            context.deleteDatabase("redforge.db")
            if (currentDb.exists()) {
                throw IllegalStateException(
                    "Could not remove the existing database"
                )
            }

            currentDb.parentFile?.mkdirs()

            copyFile(stagedDb, currentDb)

            if (preferencesFound) {
                currentPrefs.parentFile?.mkdirs()

                copyFile(stagedPrefs, currentPrefs)
            } else if (currentPrefs.exists()) {
                currentPrefs.delete()
            }

            File(currentDb.path + "-wal").delete()
            File(currentDb.path + "-shm").delete()

            if (currentPhotos.exists()) {
                currentPhotos.deleteRecursively()
            }

            if (stagingPhotos.isDirectory) {
                copyDirectory(
                    stagingPhotos,
                    currentPhotos
                )
            }

            true
        } catch (_: Exception) {
            // Best-effort rollback. The app will remain usable after a failed
            // restore because the original database/settings/photos are put back.
            try {
                File(currentDb.path + "-wal").delete()
                File(currentDb.path + "-shm").delete()
                if (rollbackDb.isFile) {
                    if (currentDb.exists()) {
                        currentDb.delete()
                    }
                    currentDb.parentFile?.mkdirs()
                    copyFile(rollbackDb, currentDb)
                } else if (!hadCurrentDb) {
                    currentDb.delete()
                }

                if (rollbackPrefs.isFile) {
                    currentPrefs.parentFile?.mkdirs()
                    copyFile(rollbackPrefs, currentPrefs)
                } else if (!hadCurrentPrefs) {
                    currentPrefs.delete()
                }

                if (rollbackPhotos.isDirectory) {
                    currentPhotos.deleteRecursively()
                    copyDirectory(
                        rollbackPhotos,
                        currentPhotos
                    )
                } else if (!hadCurrentPhotos) {
                    currentPhotos.deleteRecursively()
                }
            } catch (_: Exception) {
                // Nothing else can safely be done here.
            }

            false
        } finally {
            stagingRoot.deleteRecursively()
            rollbackRoot.deleteRecursively()
        }
    }

    fun restartApp(
        context: Context
    ) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(
                context.packageName
            )
            ?: return

        val restartIntent = Intent.makeRestartActivityTask(
            launchIntent.component
        )

        context.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun isSupportedMarker(
        markerText: String
    ): Boolean {
        return markerText.startsWith(
            "RedForge backup|format=$BACKUP_FORMAT_VERSION|"
        ) || markerText.startsWith(
            "RedForge backup — created "
        )
    }

    private fun writeTextEntry(
        zip: ZipOutputStream,
        entryName: String,
        text: String
    ) {
        zip.putNextEntry(
            ZipEntry(entryName)
        )
        zip.write(
            text.toByteArray(Charsets.UTF_8)
        )
        zip.closeEntry()
    }

    private fun addFileToZip(
        zip: ZipOutputStream,
        file: File,
        entryName: String
    ) {
        if (!file.isFile) {
            return
        }

        zip.putNextEntry(
            ZipEntry(entryName)
        )

        FileInputStream(file).use { input ->
            input.copyTo(zip)
        }

        zip.closeEntry()
    }

    private fun validateEntryName(
        name: String,
        allowMarker: Boolean
    ) {
        if (
            name.isBlank() ||
            name.startsWith("/") ||
            name.startsWith("\\") ||
            name.contains("..") ||
            name.contains("\\")
        ) {
            throw IllegalArgumentException(
                "Unsafe archive entry"
            )
        }

        val allowed =
            name == DB_ENTRY ||
                    name == PREFS_ENTRY ||
                    name.startsWith(PHOTOS_ENTRY_PREFIX) ||
                    (allowMarker && name == MARKER_ENTRY)

        if (!allowed) {
            throw IllegalArgumentException(
                "Unknown archive entry"
            )
        }
    }

    private fun readEntryLimited(
        zip: ZipInputStream,
        limit: Long
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = zip.read(buffer)

            if (read < 0) {
                break
            }

            total += read

            if (total > limit) {
                throw IllegalArgumentException(
                    "Archive entry too large"
                )
            }

            output.write(
                buffer,
                0,
                read
            )
        }

        return output.toByteArray()
    }

    private fun writeEntryLimited(
        zip: ZipInputStream,
        destination: File
    ): Long {
        destination.parentFile?.mkdirs()

        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        FileOutputStream(destination).use { output ->
            while (true) {
                val read = zip.read(buffer)

                if (read < 0) {
                    break
                }

                total += read

                if (total > MAX_ENTRY_BYTES) {
                    throw IllegalArgumentException(
                        "Archive entry too large"
                    )
                }

                output.write(
                    buffer,
                    0,
                    read
                )
            }
        }

        return total
    }

    private fun skipEntry(
        zip: ZipInputStream
    ): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val read = zip.read(buffer)

            if (read < 0) {
                break
            }

            total += read
        }

        return total
    }

    private fun validateSQLiteDatabase(
        file: File
    ) {
        FileInputStream(file).use { input ->
            val header = ByteArray(16)

            if (
                input.read(header) != 16 ||
                String(
                    header,
                    Charsets.US_ASCII
                ) != "SQLite format 3\u0000"
            ) {
                throw IllegalArgumentException(
                    "Not a SQLite database"
                )
            }
        }

        val sqlite = try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Database could not be opened",
                e
            )
        }

        try {
            sqlite.rawQuery(
                "PRAGMA integrity_check",
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IllegalArgumentException(
                        "Database integrity check failed"
                    )
                }

                val result = cursor.getString(0)

                if (!result.equals("ok", ignoreCase = true)) {
                    throw IllegalArgumentException(
                        "Database integrity check failed"
                    )
                }
            }
        } finally {
            sqlite.close()
        }
    }

    private fun copyFile(
        source: File,
        destination: File
    ) {
        if (!source.isFile) {
            return
        }

        destination.parentFile?.mkdirs()

        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun copyDirectory(
        source: File,
        destination: File
    ) {
        if (!source.isDirectory) {
            return
        }

        destination.mkdirs()

        source.listFiles()?.forEach { child ->
            val target = File(
                destination,
                child.name
            )

            if (child.isDirectory) {
                copyDirectory(
                    child,
                    target
                )
            } else {
                copyFile(child, target)
            }
        }
    }
}
