package com.aura.aura_ui.b2

import android.content.Context
import com.aura.aura_ui.data.prefs.AuraSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * User-facing B2 operations: connection checks, the agent tools' file upload/download, session
 * screenshot backup and session export bundles. Everything is opt-in and everything fails with a
 * message the UI can show — never a crash.
 */
@Singleton
class B2Service @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AuraSettings,
    private val client: B2Client,
) {
    fun isConfigured(): Boolean = client.config() != null

    fun configuredHint(): String =
        if (isConfigured()) "Bucket ${settings.b2BucketName} · ${settings.b2Endpoint}"
        else "Not configured"

    suspend fun testConnection(): Result<String> = client.ping()

    /** Uploads the file at [path] to [objectKey] (or uploads/<fileName> when not given). */
    suspend fun uploadLocalFile(
        path: String,
        objectKey: String?,
        contentType: String?,
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("No readable file at $path"))
        if (file.length() > MAX_UPLOAD_BYTES) {
            return@withContext Result.failure(IllegalArgumentException("File is larger than 10 MiB"))
        }
        // The scoped application key may only touch keys under users/ — keep every upload inside it.
        val key = objectKey?.trim()?.ifBlank { null }?.let { if (it.startsWith("users/")) it else "users/$it" } ?: "users/uploads/${file.name}"
        val guessedType = contentType?.trim()?.ifBlank { null }
            ?: file.name.substringAfterLast('.', "").let { ext ->
                if (ext.equals("png", true)) "image/png" else null
            } ?: "application/octet-stream"
        val bytes = file.readBytes()
        client.upload(key, bytes, guessedType).map { key to bytes.size.toLong() }
    }

    /** Downloads [objectKey] to [destination] (or filesDir/downloads/<objectKey name>). */
    suspend fun downloadToFile(
        objectKey: String,
        destination: String?,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (objectKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("object_key is required"))
        val target = destination?.trim()?.ifBlank { null }?.let { File(it) }
            ?: File(context.filesDir, "downloads/" + objectKey.substringAfterLast('/').ifBlank { "download.bin" })
        target.parentFile?.mkdirs()
        client.download(objectKey).map { bytes ->
            if (bytes.size > MAX_UPLOAD_BYTES) throw IllegalArgumentException("Object is larger than 10 MiB")
            target.writeBytes(bytes)
            target
        }
    }

    /** Backs up every screenshot in the session's screens/ directory; returns how many uploaded. */
    suspend fun backupSession(sessionId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!settings.b2SessionBackupEnabled || !isConfigured()) return@withContext Result.success(0)
        val dir = File(context.filesDir, "sessions/$sessionId/screens")
        if (!dir.isDirectory) return@withContext Result.success(0)
        var uploaded = 0
        dir.listFiles()?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }?.forEach { shot ->
            val key = "users/sessions/$sessionId/screens/${shot.name}"
            val result = runCatching { client.upload(key, shot.readBytes(), "image/png") }.getOrNull()
            if (result?.isSuccess == true) uploaded += 1
        }
        Result.success(uploaded)
    }

    /** Zips the whole session directory and uploads it as exports/<id>-<timestamp>.zip. */
    suspend fun exportSession(sessionId: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(B2Client.B2Error.NotConfigured())
        }
        val dir = File(context.filesDir, "sessions/$sessionId")
        if (!dir.isDirectory) return@withContext Result.failure(IllegalArgumentException("Session not found"))
        val stamp = System.currentTimeMillis()
        val zipFile = File(context.cacheDir, "aura-export-$sessionId-$stamp.zip")
        runCatching {
            zipDirectory(dir, zipFile)
            if (zipFile.length() > MAX_UPLOAD_BYTES) {
                throw IllegalArgumentException("Export is larger than 10 MiB")
            }
            client.upload("users/exports/$sessionId-$stamp.zip", zipFile.readBytes(), "application/zip")
        }.also {
            zipFile.delete()
        }
    }

    private fun zipDirectory(dir: File, target: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.path.removePrefix(dir.path).trimStart('/', '\\').replace('\\', '/')
                zip.putNextEntry(ZipEntry(rel))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    companion object {
        /** Hard cap per object. B2 does not enforce one; AURA does (10 MiB). */
        const val MAX_UPLOAD_BYTES: Long = 10L * 1024L * 1024L
    }
}
