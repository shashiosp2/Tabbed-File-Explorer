package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

object ArchiveManager {

    suspend fun listZipEntries(zipPath: String): Result<List<ArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(zipPath)
            require(file.exists() && file.isFile) { "Archive file not found" }
            val entries = mutableListOf<ArchiveEntryInfo>()
            ZipFile(file).use { zf ->
                val enumEntries = zf.entries()
                while (enumEntries.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    val entry = enumEntries.nextElement()
                    entries.add(
                        ArchiveEntryInfo(
                            name = entry.name,
                            isDirectory = entry.isDirectory,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            compressedBytes = entry.compressedSize.coerceAtLeast(0L),
                            lastModified = entry.time
                        )
                    )
                }
            }
            entries.sortedWith(compareBy<ArchiveEntryInfo> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun extractZip(
        zipPath: String,
        destinationDirPath: String,
        stripMacMetadata: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val zipFile = File(zipPath)
            val destDir = File(destinationDirPath)
            if (!destDir.exists()) destDir.mkdirs()
            val canonicalDest = destDir.canonicalPath + File.separator
            var extractedCount = 0

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(64 * 1024)
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val name = entry.name
                    if (stripMacMetadata && isMacArtifact(name)) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(destDir, name)
                    // Zip-Slip path traversal protection
                    if (!outFile.canonicalPath.startsWith(canonicalDest)) {
                        throw SecurityException("Blocked unsafe archive entry path: $name")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                bos.write(buffer, 0, read)
                            }
                        }
                        extractedCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            extractedCount
        }
    }

    suspend fun compressToZip(
        sourcePaths: List<String>,
        outputZipPath: String,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
        stripMacMetadata: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(outputZipPath)
            outFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
                zos.setLevel(compressionLevel.coerceIn(0, 9))
                val buffer = ByteArray(64 * 1024)
                for (path in sourcePaths) {
                    val src = File(path)
                    if (!src.exists()) continue
                    addFileToZip(zos, src, src.name, buffer, stripMacMetadata)
                }
            }
            outFile
        }
    }

    private suspend fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        buffer: ByteArray,
        stripMacMetadata: Boolean
    ) {
        coroutineContext.ensureActive()
        if (stripMacMetadata && isMacArtifact(entryName)) return
        if (file.isDirectory) {
            val dirEntryName = if (entryName.endsWith("/")) entryName else "$entryName/"
            zos.putNextEntry(ZipEntry(dirEntryName))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(zos, child, "$dirEntryName${child.name}", buffer, stripMacMetadata)
            }
        } else {
            val entry = ZipEntry(entryName).apply { time = file.lastModified() }
            zos.putNextEntry(entry)
            BufferedInputStream(FileInputStream(file)).use { bis ->
                var read: Int
                while (bis.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()
        }
    }

    private fun isMacArtifact(name: String): Boolean {
        return name.contains("__MACOSX") ||
            name.endsWith(".DS_Store") ||
            name.substringAfterLast('/').startsWith("._")
    }
}
