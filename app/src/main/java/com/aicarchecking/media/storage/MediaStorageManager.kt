package com.aicarchecking.media.storage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.aicarchecking.domain.model.StorageTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class ImportedFile(val file: File, val mimeType: String, val sizeBytes: Long, val displayName: String?)

data class StorageStats(
    val photosBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val reportsBytes: Long,
    val temporaryBytes: Long,
    val permanentBytes: Long,
    val thumbnailsBytes: Long,
    val freeBytes: Long,
)

sealed class StorageException(message: String) : IOException(message) {
    class TooLarge(val maxMb: Long) :
        StorageException("This file is too large to import (limit $maxMb MB). Try recording a shorter clip.")
    class InsufficientSpace :
        StorageException("Not enough free storage on your phone. Free up space or delete old evidence in Storage Manager.")
    class Unsupported(type: String) :
        StorageException("This file type ($type) is not supported. Use a photo, video or audio file.")
    class Unreadable : StorageException("We couldn't read this file. It may have been moved or deleted.")
}

/**
 * Owns every file the app writes. Evidence is copied into app-managed storage so the app never
 * depends on the original gallery URI. File paths are never logged.
 */
class MediaStorageManager(private val context: Context) {

    private val tempEvidenceDir get() = File(context.cacheDir, "evidence").apply { mkdirs() }
    private val permanentEvidenceDir get() = File(context.filesDir, "evidence").apply { mkdirs() }
    val thumbnailsDir: File get() = File(context.filesDir, "thumbs").apply { mkdirs() }
    val framesDir: File get() = File(context.cacheDir, "frames").apply { mkdirs() }
    val reportsDir: File get() = File(context.filesDir, "reports").apply { mkdirs() }
    val demoDir: File get() = File(context.filesDir, "demo").apply { mkdirs() }

    fun dirFor(tier: StorageTier): File = when (tier) {
        StorageTier.TEMPORARY -> tempEvidenceDir
        StorageTier.PERMANENT -> permanentEvidenceDir
    }

    fun newCaptureFile(extension: String): File {
        ensureFreeSpace(MIN_FREE_BYTES)
        return File(tempEvidenceDir, "${UUID.randomUUID()}.$extension")
    }

    fun newThumbnailFile(mediaId: String): File = File(thumbnailsDir, "$mediaId.jpg")

    fun newFrameFile(mediaId: String, index: Int): File = File(framesDir, "${mediaId}_f$index.jpg")

    suspend fun importFromUri(uri: Uri): ImportedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri.lastPathSegment)
        if (!(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") || mime == "application/pdf")) {
            throw StorageException.Unsupported(mime)
        }
        var displayName: String? = null
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                displayName = c.getString(0)
                if (!c.isNull(1)) declaredSize = c.getLong(1)
            }
        }
        if (declaredSize > MAX_IMPORT_BYTES) throw StorageException.TooLarge(MAX_IMPORT_BYTES / MB)
        ensureFreeSpace(maxOf(declaredSize, 0L) + MIN_FREE_BYTES)

        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val target = File(tempEvidenceDir, "${UUID.randomUUID()}.$ext")
        val input = resolver.openInputStream(uri) ?: throw StorageException.Unreadable()
        var copied = 0L
        try {
            input.use { src ->
                target.outputStream().use { dst ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = src.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_IMPORT_BYTES) throw StorageException.TooLarge(MAX_IMPORT_BYTES / MB)
                        dst.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        ImportedFile(target, mime, copied, displayName)
    }

    /** Moves a file into permanent storage and returns its new path. */
    suspend fun promoteToPermanent(path: String): String = withContext(Dispatchers.IO) {
        val src = File(path)
        if (src.parentFile?.canonicalPath == permanentEvidenceDir.canonicalPath) return@withContext path
        if (!src.exists()) throw StorageException.Unreadable()
        val dst = File(permanentEvidenceDir, src.name)
        if (!src.renameTo(dst)) {
            src.copyTo(dst, overwrite = true)
            src.delete()
        }
        dst.absolutePath
    }

    fun deleteQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        val f = File(path)
        if (isManaged(f)) f.delete()
    }

    /** Only files inside our own directories may ever be deleted. */
    private fun isManaged(f: File): Boolean {
        val p = runCatching { f.canonicalPath }.getOrNull() ?: return false
        return p.startsWith(context.filesDir.canonicalPath) || p.startsWith(context.cacheDir.canonicalPath)
    }

    suspend fun stats(): StorageStats = withContext(Dispatchers.IO) {
        var photos = 0L; var videos = 0L; var audio = 0L
        fun classify(dir: File) {
            dir.listFiles()?.forEach { f ->
                when (guessMime(f.name).substringBefore('/')) {
                    "image" -> photos += f.length()
                    "video" -> videos += f.length()
                    "audio" -> audio += f.length()
                }
            }
        }
        classify(tempEvidenceDir); classify(permanentEvidenceDir); classify(demoDir)
        StorageStats(
            photosBytes = photos,
            videosBytes = videos,
            audioBytes = audio,
            reportsBytes = dirSize(reportsDir),
            temporaryBytes = dirSize(tempEvidenceDir) + dirSize(framesDir),
            permanentBytes = dirSize(permanentEvidenceDir),
            thumbnailsBytes = dirSize(thumbnailsDir),
            freeBytes = freeBytes(),
        )
    }

    suspend fun clearFrameCache() = withContext(Dispatchers.IO) { framesDir.listFiles()?.forEach { it.delete() } }

    suspend fun deleteAllReports() = withContext(Dispatchers.IO) { reportsDir.listFiles()?.forEach { it.delete() } }

    /** Removes files in evidence/thumbnail folders that no database record references. */
    suspend fun cleanOrphans(referencedPaths: Set<String>): Int = withContext(Dispatchers.IO) {
        val referenced = referencedPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        var removed = 0
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS // never touch files that may be mid-write
        listOf(tempEvidenceDir, permanentEvidenceDir, thumbnailsDir, framesDir).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff && f.canonicalPath !in referenced && f.delete()) removed++
            }
        }
        removed
    }

    /** Copies a vehicle profile photo into app storage as a downscaled JPEG. */
    suspend fun importVehiclePhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val imported = importFromUri(uri)
        try {
            val bmp = com.aicarchecking.media.image.ImageUtils.decodeSampled(imported.file, 1280)
                ?: throw StorageException.Unreadable()
            val out = File(File(context.filesDir, "vehicles").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
            com.aicarchecking.media.image.ImageUtils.saveJpeg(bmp, out, 85)
            bmp.recycle()
            out.absolutePath
        } finally {
            imported.file.delete()
        }
    }

    fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    private fun ensureFreeSpace(required: Long) {
        if (freeBytes() < required) throw StorageException.InsufficientSpace()
    }

    private fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        private const val MB = 1024L * 1024L
        const val MAX_IMPORT_BYTES = 500L * MB
        private const val MIN_FREE_BYTES = 50L * MB
        private const val ORPHAN_GRACE_MS = 60L * 60 * 1000

        fun guessMime(name: String?): String {
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
        }
    }
}
