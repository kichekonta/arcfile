package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.github.junrar.Archive
import com.github.junrar.exception.BadRarArchiveException
import com.github.junrar.exception.RarException
import com.github.junrar.exception.UnsupportedRarV5Exception
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Unified pure-JVM archive engine supporting:
 * - ZIP (.zip): inspect, extract, and create (with optional AES-256 encryption + compression levels)
 * - 7-Zip (.7z): inspect, extract (including password-protected), and create (STORE/DEFLATE/LZMA2)
 * - RAR (.rar): inspect & extract (v1-v4, including password-protected; RAR5 detected with clear message)
 */
class ArchiveEngine(private val context: Context) {

    data class ProgressUpdate(
        val processedItems: Int,
        val totalItems: Int,
        val currentName: String,
        val processedBytes: Long,
        val totalBytes: Long
    )

    suspend fun inspectArchive(
        item: FileItem,
        password: String? = null,
        maxEntries: Int = 1500
    ): Result<ArchiveInspectionResult> {
        return try {
            val format = ArchiveFormat.fromExtension(item.extension)
                ?: return Result.failure(IllegalArgumentException("Unsupported archive format: .${item.extension}"))

            val tempArchiveFile = if (item.isSafUri) copySafToTempFile(item) else null
            val localFile = tempArchiveFile ?: File(item.pathOrUri)
            try {
                require(localFile.exists() && localFile.isFile) { "Archive file not found" }
                val result = when (format) {
                    ArchiveFormat.ZIP -> inspectZip(localFile, password, maxEntries)
                    ArchiveFormat.SEVEN_Z -> inspectSevenZ(localFile, password, maxEntries)
                    ArchiveFormat.RAR -> inspectRar(localFile, password, maxEntries)
                }
                Result.success(result)
            } finally {
                tempArchiveFile?.delete()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun extractArchive(
        item: FileItem,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        createSubfolder: Boolean = true,
        customSubfolderName: String? = null,
        overwriteMode: ArchiveOverwriteMode = ArchiveOverwriteMode.OVERWRITE,
        selectedEntryPaths: Set<String>? = null,
        password: String? = null,
        onProgress: ((ProgressUpdate) -> Unit)? = null
    ): Result<Int> {
        val format = ArchiveFormat.fromExtension(item.extension)
            ?: return Result.failure(IllegalArgumentException("Unsupported archive format: .${item.extension}"))

        var tempSourceFile: File? = null
        var safStagingDir: File? = null
        return try {
            tempSourceFile = if (item.isSafUri) copySafToTempFile(item) else null
            val archiveFile = tempSourceFile ?: File(item.pathOrUri)
            require(archiveFile.exists() && archiveFile.isFile) { "Archive file not found" }

            val folderBase = sanitizeArchiveBaseName(
                customSubfolderName?.takeIf { it.isNotBlank() }
                    ?: item.name.substringBeforeLast('.', item.name)
            )
            val normalizedFilter = selectedEntryPaths
                ?.mapNotNull { it.replace('\\', '/').trimStart('/').takeIf(String::isNotBlank) }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }

            val outRoot: File = if (isTargetSaf) {
                File(context.cacheDir, "arc_extract_stage_${UUID.randomUUID().toString().take(8)}").apply {
                    mkdirs()
                }.also { safStagingDir = it }
            } else {
                val baseDir = File(targetParentPathOrUri).apply { if (!exists()) mkdirs() }
                if (createSubfolder) {
                    File(baseDir, folderBase).apply { mkdirs() }
                } else {
                    baseDir
                }
            }

            val extractedCount = when (format) {
                ArchiveFormat.ZIP -> extractZip(archiveFile, outRoot, password, overwriteMode, normalizedFilter, onProgress)
                ArchiveFormat.SEVEN_Z -> extractSevenZ(archiveFile, outRoot, password, overwriteMode, normalizedFilter, onProgress)
                ArchiveFormat.RAR -> extractRar(archiveFile, outRoot, password, overwriteMode, normalizedFilter, onProgress)
            }

            if (isTargetSaf && safStagingDir != null) {
                val targetSafParent = if (createSubfolder) {
                    createOrFindSafSubdir(Uri.parse(targetParentPathOrUri), folderBase)
                } else {
                    normalizeSafDocumentUri(Uri.parse(targetParentPathOrUri))
                }
                uploadDirectoryToSaf(outRoot, targetSafParent)
            }

            Result.success(extractedCount)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tempSourceFile?.delete()
            safStagingDir?.deleteRecursively()
        }
    }

    suspend fun testArchive(
        item: FileItem,
        password: String? = null
    ): Result<ArchiveTestResult> {
        val format = ArchiveFormat.fromExtension(item.extension)
            ?: return Result.failure(IllegalArgumentException("Unsupported archive format: .${item.extension}"))

        var tempSourceFile: File? = null
        return try {
            tempSourceFile = if (item.isSafUri) copySafToTempFile(item) else null
            val archiveFile = tempSourceFile ?: File(item.pathOrUri)
            require(archiveFile.exists() && archiveFile.isFile) { "Archive file not found" }

            val buffer = ByteArray(64 * 1024)
            var testedFiles = 0
            when (format) {
                ArchiveFormat.ZIP -> {
                    val zip = ZipFile(archiveFile)
                    val headers = zip.fileHeaders
                    val requiresPassword = headers.any { !it.isDirectory && it.isEncrypted }
                    if (requiresPassword && password.isNullOrEmpty()) {
                        return Result.success(
                            ArchiveTestResult(0, false, "Password required to test encrypted entries.")
                        )
                    }
                    if (!password.isNullOrEmpty()) {
                        zip.setPassword(password.toCharArray())
                    }
                    for (header in headers) {
                        currentCoroutineContext().ensureActive()
                        if (header.isDirectory) continue
                        zip.getInputStream(header).use { input ->
                            while (input.read(buffer) >= 0) {
                                currentCoroutineContext().ensureActive()
                            }
                        }
                        testedFiles++
                    }
                }
                ArchiveFormat.SEVEN_Z -> {
                    openSevenZFile(archiveFile, password).use { archive ->
                        var entry = archive.nextEntry
                        while (entry != null) {
                            currentCoroutineContext().ensureActive()
                            if (!entry.isDirectory) {
                                while (archive.read(buffer) >= 0) {
                                    currentCoroutineContext().ensureActive()
                                }
                                testedFiles++
                            }
                            entry = archive.nextEntry
                        }
                    }
                }
                ArchiveFormat.RAR -> {
                    val rar = if (!password.isNullOrEmpty()) Archive(archiveFile, password) else Archive(archiveFile)
                    rar.use { archive ->
                        val nullOut = object : java.io.OutputStream() {
                            override fun write(b: Int) {}
                            override fun write(b: ByteArray, off: Int, len: Int) {}
                        }
                        for (header in archive.fileHeaders) {
                            currentCoroutineContext().ensureActive()
                            if (header.isDirectory) continue
                            archive.extractFile(header, nullOut)
                            testedFiles++
                        }
                    }
                }
            }
            Result.success(
                ArchiveTestResult(
                    testedFiles = testedFiles,
                    passed = true,
                    summary = "Integrity verified: $testedFiles file(s) passed CRC & stream checks."
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val msg = t.localizedMessage?.takeIf { it.isNotBlank() } ?: "Archive verification failed"
            Result.success(ArchiveTestResult(testedFiles = 0, passed = false, summary = msg))
        } finally {
            tempSourceFile?.delete()
        }
    }

    suspend fun extractSingleEntryToCache(
        item: FileItem,
        entryPath: String,
        password: String? = null
    ): Result<File> {
        val format = ArchiveFormat.fromExtension(item.extension)
            ?: return Result.failure(IllegalArgumentException("Unsupported archive format: .${item.extension}"))
        val normalizedTarget = entryPath.replace('\\', '/').trimStart('/').trimEnd('/')
        if (normalizedTarget.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid archive entry"))
        }

        var tempSourceFile: File? = null
        return try {
            tempSourceFile = if (item.isSafUri) copySafToTempFile(item) else null
            val archiveFile = tempSourceFile ?: File(item.pathOrUri)
            require(archiveFile.exists() && archiveFile.isFile) { "Archive file not found" }

            val cacheRoot = File(context.cacheDir, "arc_entry_preview_${UUID.randomUUID().toString().take(8)}").apply {
                mkdirs()
            }
            val fileName = normalizedTarget.substringAfterLast('/').ifBlank { "preview.bin" }
            val outFile = File(cacheRoot, fileName)
            val buffer = ByteArray(64 * 1024)
            var found = false

            when (format) {
                ArchiveFormat.ZIP -> {
                    val zip = ZipFile(archiveFile)
                    if (!password.isNullOrEmpty()) zip.setPassword(password.toCharArray())
                    val header = zip.fileHeaders.firstOrNull {
                        !it.isDirectory && it.fileName.replace('\\', '/').trimStart('/') == normalizedTarget
                    }
                    if (header != null) {
                        zip.getInputStream(header).use { input ->
                            FileOutputStream(outFile).use { output ->
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val r = input.read(buffer)
                                    if (r < 0) break
                                    output.write(buffer, 0, r)
                                }
                            }
                        }
                        found = true
                    }
                }
                ArchiveFormat.SEVEN_Z -> {
                    openSevenZFile(archiveFile, password).use { archive ->
                        var entry = archive.nextEntry
                        while (entry != null) {
                            currentCoroutineContext().ensureActive()
                            val clean = (entry.name ?: "").replace('\\', '/').trimStart('/')
                            if (!entry.isDirectory && clean == normalizedTarget) {
                                FileOutputStream(outFile).use { output ->
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val r = archive.read(buffer)
                                        if (r < 0) break
                                        output.write(buffer, 0, r)
                                    }
                                }
                                found = true
                                break
                            }
                            entry = archive.nextEntry
                        }
                    }
                }
                ArchiveFormat.RAR -> {
                    val rar = if (!password.isNullOrEmpty()) Archive(archiveFile, password) else Archive(archiveFile)
                    rar.use { archive ->
                        val header = archive.fileHeaders.firstOrNull {
                            !it.isDirectory && it.fileName.replace('\\', '/').trimStart('/') == normalizedTarget
                        }
                        if (header != null) {
                            FileOutputStream(outFile).use { output ->
                                archive.extractFile(header, output)
                            }
                            found = true
                        }
                    }
                }
            }

            if (!found) {
                outFile.delete()
                cacheRoot.deleteRecursively()
                Result.failure(IllegalArgumentException("Entry '$fileName' not found in archive"))
            } else {
                Result.success(outFile)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tempSourceFile?.delete()
        }
    }

    suspend fun createArchive(
        items: List<FileItem>,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        archiveBaseName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        password: String? = null,
        onProgress: ((ProgressUpdate) -> Unit)? = null
    ): Result<FileItem> {
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least one file or folder to compress"))
        }
        if (!format.supportsCreation) {
            return Result.failure(
                IllegalArgumentException("${format.label} is extract-only. Choose ZIP or 7-Zip to create an archive.")
            )
        }

        val cleanPassword = password?.takeIf { it.isNotEmpty() }
        val cleanBase = sanitizeArchiveBaseName(
            archiveBaseName.trim()
                .removeSuffix(".${format.extension}")
                .removeSuffix(".${format.extension.uppercase()}")
        )
        val desiredFileName = "$cleanBase.${format.extension}"

        var safSourceStageDir: File? = null
        var tempOutputArchive: File? = null
        return try {
            // Prepare local staging for SAF inputs so both ZIP and 7Z engines read real File instances uniformly
            val resolvedSources = mutableListOf<StagedSource>()
            for (item in items) {
                currentCoroutineContext().ensureActive()
                if (item.isSafUri) {
                    val stage = safSourceStageDir ?: File(
                        context.cacheDir,
                        "arc_src_stage_${UUID.randomUUID().toString().take(8)}"
                    ).apply { mkdirs() }.also { safSourceStageDir = it }
                    val stagedFile = stageSafItemToLocal(item, stage)
                    resolvedSources += StagedSource(stagedFile, item.name)
                } else {
                    val f = File(item.pathOrUri)
                    if (f.exists()) {
                        resolvedSources += StagedSource(f, item.name)
                    }
                }
            }
            require(resolvedSources.isNotEmpty()) { "No readable files found to archive" }

            // Collect flat entry list (relative path inside archive -> source File)
            val entries = collectArchiveEntries(resolvedSources)
            val totalItems = entries.size.coerceAtLeast(1)
            val totalBytes = entries.sumOf { if (it.file.isDirectory) 0L else it.file.length().coerceAtLeast(0L) }

            val outputFile: File = if (isTargetSaf) {
                File(context.cacheDir, "arc_out_${UUID.randomUUID().toString().take(8)}.${format.extension}").also {
                    tempOutputArchive = it
                }
            } else {
                val targetDir = File(targetParentPathOrUri).apply { if (!exists()) mkdirs() }
                resolveUniqueArchiveFile(targetDir, cleanBase, format.extension)
            }

            try {
                when (format) {
                    ArchiveFormat.ZIP -> writeZipArchive(
                        outputFile = outputFile,
                        entries = entries,
                        totalItems = totalItems,
                        totalBytes = totalBytes,
                        compressionLevel = compressionLevel,
                        password = cleanPassword,
                        onProgress = onProgress
                    )
                    ArchiveFormat.SEVEN_Z -> writeSevenZArchive(
                        outputFile = outputFile,
                        entries = entries,
                        totalItems = totalItems,
                        totalBytes = totalBytes,
                        compressionLevel = compressionLevel,
                        onProgress = onProgress
                    )
                    ArchiveFormat.RAR -> error("RAR creation is unsupported")
                }
            } catch (t: Throwable) {
                runCatching { outputFile.delete() }
                throw t
            }

            val resultItem = if (isTargetSaf) {
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    normalizeSafDocumentUri(Uri.parse(targetParentPathOrUri)),
                    format.mimeType,
                    desiredFileName
                ) ?: error("Unable to create archive file in mounted drive")
                try {
                    context.contentResolver.openOutputStream(createdUri)?.use { out ->
                        outputFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: error("Unable to write archive stream")
                } catch (t: Throwable) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, createdUri) }
                    throw t
                }
                val finalName = resolveSafDisplayName(createdUri) ?: desiredFileName
                FileItem(
                    id = createdUri.toString(),
                    name = finalName,
                    pathOrUri = createdUri.toString(),
                    isDirectory = false,
                    sizeBytes = outputFile.length(),
                    lastModifiedMillis = System.currentTimeMillis(),
                    mimeType = format.mimeType,
                    extension = format.extension,
                    isSafUri = true,
                    parentPathOrUri = targetParentPathOrUri,
                    childCount = null
                )
            } else {
                FileItem(
                    id = outputFile.absolutePath,
                    name = outputFile.name,
                    pathOrUri = outputFile.absolutePath,
                    isDirectory = false,
                    sizeBytes = outputFile.length(),
                    lastModifiedMillis = outputFile.lastModified(),
                    mimeType = format.mimeType,
                    extension = format.extension,
                    isSafUri = false,
                    parentPathOrUri = outputFile.parent ?: targetParentPathOrUri,
                    childCount = null
                )
            }

            Result.success(resultItem)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tempOutputArchive?.delete()
            safSourceStageDir?.deleteRecursively()
        }
    }

    // region Inspection implementations

    private fun inspectZip(file: File, password: String?, maxEntries: Int): ArchiveInspectionResult {
        val zip = ZipFile(file)
        if (!password.isNullOrEmpty()) {
            zip.setPassword(password.toCharArray())
        }
        val headers = zip.fileHeaders
        val anyEncrypted = headers.any { it.isEncrypted } || runCatching { zip.isEncrypted }.getOrDefault(false)
        var totalUncompressed = 0L
        var totalCompressed = 0L
        headers.forEach { h ->
            if (!h.isDirectory) {
                totalUncompressed += h.uncompressedSize.coerceAtLeast(0L)
                totalCompressed += h.compressedSize.coerceAtLeast(0L)
            }
        }
        val entries = headers.take(maxEntries).map { h ->
            ZipEntryInfo(
                name = h.fileName.replace('\\', '/'),
                isDirectory = h.isDirectory,
                uncompressedSize = h.uncompressedSize.coerceAtLeast(0L),
                isEncrypted = h.isEncrypted,
                compressedSize = h.compressedSize.coerceAtLeast(0L),
                lastModifiedMillis = h.lastModifiedTimeEpoch.coerceAtLeast(0L)
            )
        }
        return ArchiveInspectionResult(
            format = ArchiveFormat.ZIP,
            entries = entries,
            isEncrypted = anyEncrypted,
            totalEntryCount = headers.size,
            totalUncompressedBytes = totalUncompressed,
            totalCompressedBytes = totalCompressed.coerceAtLeast(0L)
        )
    }

    private fun inspectSevenZ(file: File, password: String?, maxEntries: Int): ArchiveInspectionResult {
        return try {
            val entries = mutableListOf<ZipEntryInfo>()
            var encrypted = false
            var totalCount = 0
            var totalUncompressed = 0L
            openSevenZFile(file, password).use { sevenZ ->
                for (entry in sevenZ.entries) {
                    totalCount++
                    val isEntryEncrypted = entry.contentMethods?.any {
                        it.method == SevenZMethod.AES256SHA256
                    } == true
                    if (isEntryEncrypted) encrypted = true
                    if (!entry.isDirectory) {
                        totalUncompressed += entry.size.coerceAtLeast(0L)
                    }
                    if (entries.size < maxEntries) {
                        entries += ZipEntryInfo(
                            name = (entry.name ?: "").replace('\\', '/'),
                            isDirectory = entry.isDirectory,
                            uncompressedSize = entry.size.coerceAtLeast(0L),
                            isEncrypted = isEntryEncrypted,
                            compressedSize = 0L,
                            lastModifiedMillis = runCatching {
                                if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else 0L
                            }.getOrDefault(0L)
                        )
                    }
                }
            }
            ArchiveInspectionResult(
                format = ArchiveFormat.SEVEN_Z,
                entries = entries,
                isEncrypted = encrypted,
                totalEntryCount = totalCount,
                totalUncompressedBytes = totalUncompressed,
                totalCompressedBytes = file.length().coerceAtLeast(0L)
            )
        } catch (_: PasswordRequiredException) {
            ArchiveInspectionResult(
                format = ArchiveFormat.SEVEN_Z,
                entries = emptyList(),
                isEncrypted = true,
                errorNote = if (password.isNullOrEmpty()) {
                    "This 7-Zip archive has encrypted headers. Enter the password below to inspect contents."
                } else {
                    "Incorrect password for this 7-Zip archive."
                }
            )
        } catch (io: IOException) {
            if (io.message?.contains("password", ignoreCase = true) == true ||
                io.message?.contains("corrupt", ignoreCase = true) == true && !password.isNullOrEmpty()
            ) {
                ArchiveInspectionResult(
                    format = ArchiveFormat.SEVEN_Z,
                    entries = emptyList(),
                    isEncrypted = true,
                    errorNote = "Could not unlock archive — check the password and try again."
                )
            } else {
                throw io
            }
        }
    }

    private fun inspectRar(file: File, password: String?, maxEntries: Int): ArchiveInspectionResult {
        return try {
            val rar = if (!password.isNullOrEmpty()) Archive(file, password) else Archive(file)
            rar.use { archive ->
                val headers = archive.fileHeaders
                val anyEncrypted = headers.any { it.isEncrypted } || runCatching { archive.isEncrypted }.getOrDefault(false)
                var totalUncompressed = 0L
                var totalCompressed = 0L
                headers.forEach { h ->
                    if (!h.isDirectory) {
                        totalUncompressed += h.fullUnpackSize.coerceAtLeast(0L)
                        totalCompressed += h.fullPackSize.coerceAtLeast(0L)
                    }
                }
                val entries = headers.take(maxEntries).map { h ->
                    ZipEntryInfo(
                        name = h.fileName.replace('\\', '/'),
                        isDirectory = h.isDirectory,
                        uncompressedSize = h.fullUnpackSize.coerceAtLeast(0L),
                        isEncrypted = h.isEncrypted,
                        compressedSize = h.fullPackSize.coerceAtLeast(0L),
                        lastModifiedMillis = h.mTime?.time ?: 0L
                    )
                }
                ArchiveInspectionResult(
                    format = ArchiveFormat.RAR,
                    entries = entries,
                    isEncrypted = anyEncrypted,
                    totalEntryCount = headers.size,
                    totalUncompressedBytes = totalUncompressed,
                    totalCompressedBytes = totalCompressed.coerceAtLeast(0L)
                )
            }
        } catch (_: UnsupportedRarV5Exception) {
            ArchiveInspectionResult(
                format = ArchiveFormat.RAR,
                entries = emptyList(),
                isEncrypted = false,
                errorNote = "RAR5 archives cannot be decoded by the built-in extractor. Use 'Open As' to open it with an external RAR app."
            )
        } catch (bad: BadRarArchiveException) {
            throw IOException(bad.localizedMessage ?: "Invalid or corrupted RAR archive", bad)
        } catch (rarEx: RarException) {
            throw IOException(rarEx.localizedMessage ?: "Unable to read RAR archive", rarEx)
        }
    }

    // endregion

    // region Extraction implementations

    private suspend fun extractZip(
        archiveFile: File,
        outRoot: File,
        password: String?,
        overwriteMode: ArchiveOverwriteMode,
        selectedEntryPaths: Set<String>?,
        onProgress: ((ProgressUpdate) -> Unit)?
    ): Int {
        val outCanonical = outRoot.canonicalPath
        val zip = ZipFile(archiveFile)
        val headers = zip.fileHeaders
        val matchedHeaders = if (selectedEntryPaths == null) {
            headers
        } else {
            headers.filter { h ->
                matchesSelectedEntry(h.fileName, h.isDirectory, selectedEntryPaths)
            }
        }
        val requiresPassword = matchedHeaders.any { !it.isDirectory && it.isEncrypted }
        if (requiresPassword && password.isNullOrEmpty()) {
            throw IllegalStateException("Password required to extract this ZIP archive")
        }
        if (!password.isNullOrEmpty()) {
            zip.setPassword(password.toCharArray())
        }

        val totalItems = matchedHeaders.size.coerceAtLeast(1)
        val totalBytes = matchedHeaders.sumOf { if (it.isDirectory) 0L else it.uncompressedSize.coerceAtLeast(0L) }
        var processedItems = 0
        var processedBytes = 0L
        var extractedFiles = 0
        val buffer = ByteArray(64 * 1024)

        for (header in matchedHeaders) {
            currentCoroutineContext().ensureActive()
            val cleanName = header.fileName.replace('\\', '/').trimStart('/')
            if (cleanName.isBlank()) {
                processedItems++
                continue
            }
            val candidateFile = safeResolveEntry(outRoot, outCanonical, cleanName) ?: continue
            if (header.isDirectory) {
                candidateFile.mkdirs()
                processedItems++
                onProgress?.invoke(ProgressUpdate(processedItems, totalItems, cleanName, processedBytes, totalBytes))
                continue
            }
            val outFile = resolveOverwriteTarget(candidateFile, overwriteMode)
            if (outFile == null) {
                processedItems++
                processedBytes += header.uncompressedSize.coerceAtLeast(0L)
                onProgress?.invoke(ProgressUpdate(processedItems, totalItems, candidateFile.name, processedBytes, totalBytes))
                continue
            }
            outFile.parentFile?.mkdirs()
            onProgress?.invoke(ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes))
            try {
                zip.getInputStream(header).use { input ->
                    FileOutputStream(outFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            processedBytes += read.toLong()
                            onProgress?.invoke(
                                ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes)
                            )
                        }
                    }
                }
                extractedFiles++
                processedItems++
                onProgress?.invoke(ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes))
            } catch (ze: ZipException) {
                runCatching { outFile.delete() }
                if (ze.type == ZipException.Type.WRONG_PASSWORD) {
                    throw IllegalStateException("Incorrect password for ZIP archive", ze)
                }
                throw ze
            } catch (t: Throwable) {
                runCatching { outFile.delete() }
                throw t
            }
        }
        return extractedFiles
    }

    private suspend fun extractSevenZ(
        archiveFile: File,
        outRoot: File,
        password: String?,
        overwriteMode: ArchiveOverwriteMode,
        selectedEntryPaths: Set<String>?,
        onProgress: ((ProgressUpdate) -> Unit)?
    ): Int {
        val outCanonical = outRoot.canonicalPath
        val sevenZ = try {
            openSevenZFile(archiveFile, password)
        } catch (pre: PasswordRequiredException) {
            throw IllegalStateException(
                if (password.isNullOrEmpty()) "Password required for 7-Zip archive" else "Incorrect 7-Zip password",
                pre
            )
        }

        return sevenZ.use { archive ->
            val allEntries = archive.entries.toList().filter { entry ->
                selectedEntryPaths == null || matchesSelectedEntry(entry.name.orEmpty(), entry.isDirectory, selectedEntryPaths)
            }
            val totalItems = allEntries.size.coerceAtLeast(1)
            val totalBytes = allEntries.sumOf { if (it.isDirectory) 0L else it.size.coerceAtLeast(0L) }
            var processedItems = 0
            var processedBytes = 0L
            var extractedFiles = 0
            val buffer = ByteArray(64 * 1024)

            var entry = try {
                archive.nextEntry
            } catch (pre: PasswordRequiredException) {
                throw IllegalStateException("Password required for 7-Zip archive", pre)
            }

            while (entry != null) {
                currentCoroutineContext().ensureActive()
                val rawName = (entry.name ?: "").replace('\\', '/').trimStart('/')
                if (rawName.isBlank() ||
                    (selectedEntryPaths != null && !matchesSelectedEntry(rawName, entry.isDirectory, selectedEntryPaths))
                ) {
                    entry = archive.nextEntry
                    continue
                }
                val candidateFile = safeResolveEntry(outRoot, outCanonical, rawName)
                if (candidateFile == null) {
                    processedItems++
                    entry = archive.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    candidateFile.mkdirs()
                    processedItems++
                    onProgress?.invoke(ProgressUpdate(processedItems, totalItems, rawName, processedBytes, totalBytes))
                } else {
                    val outFile = resolveOverwriteTarget(candidateFile, overwriteMode)
                    if (outFile == null) {
                        processedItems++
                        processedBytes += entry.size.coerceAtLeast(0L)
                        onProgress?.invoke(ProgressUpdate(processedItems, totalItems, candidateFile.name, processedBytes, totalBytes))
                    } else {
                        outFile.parentFile?.mkdirs()
                        onProgress?.invoke(ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes))
                        try {
                            FileOutputStream(outFile).use { output ->
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = archive.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    processedBytes += read.toLong()
                                    onProgress?.invoke(
                                        ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes)
                                    )
                                }
                            }
                            extractedFiles++
                            processedItems++
                            onProgress?.invoke(
                                ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes)
                            )
                        } catch (pre: PasswordRequiredException) {
                            runCatching { outFile.delete() }
                            throw IllegalStateException(
                                if (password.isNullOrEmpty()) "Password required for 7-Zip archive" else "Incorrect 7-Zip password",
                                pre
                            )
                        } catch (io: IOException) {
                            runCatching { outFile.delete() }
                            val msg = io.message.orEmpty()
                            if (msg.contains("password", ignoreCase = true) ||
                                msg.contains("bad tag", ignoreCase = true) ||
                                (!password.isNullOrEmpty() && msg.contains("Corrupted", ignoreCase = true))
                            ) {
                                throw IllegalStateException("Incorrect password or corrupted 7-Zip archive", io)
                            }
                            throw io
                        } catch (t: Throwable) {
                            runCatching { outFile.delete() }
                            throw t
                        }
                    }
                }
                entry = archive.nextEntry
            }
            extractedFiles
        }
    }

    private suspend fun extractRar(
        archiveFile: File,
        outRoot: File,
        password: String?,
        overwriteMode: ArchiveOverwriteMode,
        selectedEntryPaths: Set<String>?,
        onProgress: ((ProgressUpdate) -> Unit)?
    ): Int {
        val outCanonical = outRoot.canonicalPath
        val rar = try {
            if (!password.isNullOrEmpty()) Archive(archiveFile, password) else Archive(archiveFile)
        } catch (v5: UnsupportedRarV5Exception) {
            throw IllegalStateException(
                "RAR5 format is not supported by the built-in extractor. Use 'Open As' to open with an external RAR app.",
                v5
            )
        } catch (bad: BadRarArchiveException) {
            throw IOException(bad.localizedMessage ?: "Invalid or corrupted RAR archive", bad)
        }

        return rar.use { archive ->
            val headers = archive.fileHeaders.filter { h ->
                selectedEntryPaths == null || matchesSelectedEntry(h.fileName, h.isDirectory, selectedEntryPaths)
            }
            val requiresPassword = headers.any { !it.isDirectory && it.isEncrypted } ||
                runCatching { archive.isEncrypted }.getOrDefault(false)
            if (requiresPassword && password.isNullOrEmpty()) {
                throw IllegalStateException("Password required to extract this RAR archive")
            }

            val totalItems = headers.size.coerceAtLeast(1)
            val totalBytes = headers.sumOf { if (it.isDirectory) 0L else it.fullUnpackSize.coerceAtLeast(0L) }
            var processedItems = 0
            var processedBytes = 0L
            var extractedFiles = 0

            for (header in headers) {
                currentCoroutineContext().ensureActive()
                val cleanName = header.fileName.replace('\\', '/').trimStart('/')
                if (cleanName.isBlank()) {
                    processedItems++
                    continue
                }
                val candidateFile = safeResolveEntry(outRoot, outCanonical, cleanName) ?: continue
                if (header.isDirectory) {
                    candidateFile.mkdirs()
                    processedItems++
                    onProgress?.invoke(ProgressUpdate(processedItems, totalItems, cleanName, processedBytes, totalBytes))
                    continue
                }
                val outFile = resolveOverwriteTarget(candidateFile, overwriteMode)
                if (outFile == null) {
                    processedItems++
                    processedBytes += header.fullUnpackSize.coerceAtLeast(0L)
                    onProgress?.invoke(ProgressUpdate(processedItems, totalItems, candidateFile.name, processedBytes, totalBytes))
                    continue
                }
                outFile.parentFile?.mkdirs()
                onProgress?.invoke(ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes))
                try {
                    FileOutputStream(outFile).use { out ->
                        archive.extractFile(header, out)
                    }
                    extractedFiles++
                    processedItems++
                    processedBytes += header.fullUnpackSize.coerceAtLeast(outFile.length())
                    onProgress?.invoke(ProgressUpdate(processedItems, totalItems, outFile.name, processedBytes, totalBytes))
                } catch (re: RarException) {
                    runCatching { outFile.delete() }
                    val msg = re.localizedMessage.orEmpty()
                    if (header.isEncrypted && (msg.contains("crc", ignoreCase = true) || msg.contains("password", ignoreCase = true))) {
                        throw IllegalStateException("Incorrect password or corrupted encrypted RAR entry", re)
                    }
                    throw IOException(msg.ifBlank { "Failed extracting RAR entry '${outFile.name}'" }, re)
                } catch (t: Throwable) {
                    runCatching { outFile.delete() }
                    throw t
                }
            }
            extractedFiles
        }
    }

    // endregion

    // region Creation implementations

    private data class StagedSource(val file: File, val entryRootName: String)
    private data class ArchiveEntrySource(val file: File, val archivePath: String)

    private suspend fun collectArchiveEntries(sources: List<StagedSource>): List<ArchiveEntrySource> {
        val collected = mutableListOf<ArchiveEntrySource>()
        val usedRootNames = HashSet<String>()

        fun uniqueRootName(raw: String): String {
            if (usedRootNames.add(raw)) return raw
            var idx = 2
            while (!usedRootNames.add("${raw}_$idx")) idx++
            return "${raw}_$idx"
        }

        suspend fun walk(file: File, relPath: String) {
            currentCoroutineContext().ensureActive()
            if (file.isDirectory) {
                collected += ArchiveEntrySource(file, "$relPath/")
                val children = file.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty()
                for (child in children) {
                    walk(child, "$relPath/${child.name}")
                }
            } else {
                collected += ArchiveEntrySource(file, relPath)
            }
        }

        for (src in sources) {
            val rootEntry = uniqueRootName(src.entryRootName.trim('/').ifBlank { src.file.name })
            walk(src.file, rootEntry)
        }
        return collected
    }

    private suspend fun writeZipArchive(
        outputFile: File,
        entries: List<ArchiveEntrySource>,
        totalItems: Int,
        totalBytes: Long,
        compressionLevel: ArchiveCompressionLevel,
        password: String?,
        onProgress: ((ProgressUpdate) -> Unit)?
    ) {
        val method = if (compressionLevel == ArchiveCompressionLevel.STORE) {
            CompressionMethod.STORE
        } else {
            CompressionMethod.DEFLATE
        }
        val level = when (compressionLevel) {
            ArchiveCompressionLevel.STORE -> CompressionLevel.NO_COMPRESSION
            ArchiveCompressionLevel.FAST -> CompressionLevel.FASTEST
            ArchiveCompressionLevel.NORMAL -> CompressionLevel.NORMAL
            ArchiveCompressionLevel.BEST -> CompressionLevel.ULTRA
        }
        val encrypt = !password.isNullOrEmpty()
        var processedItems = 0
        var processedBytes = 0L
        val buffer = ByteArray(64 * 1024)

        val zipStream = if (encrypt) {
            ZipOutputStream(FileOutputStream(outputFile), password!!.toCharArray())
        } else {
            ZipOutputStream(FileOutputStream(outputFile))
        }

        zipStream.use { zos ->
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                val params = ZipParameters().apply {
                    fileNameInZip = entry.archivePath
                    isOverrideExistingFilesInZip = true
                    if (entry.file.isDirectory) {
                        compressionMethod = CompressionMethod.STORE
                        this.compressionLevel = CompressionLevel.NO_COMPRESSION
                        isEncryptFiles = false
                    } else {
                        compressionMethod = method
                        this.compressionLevel = level
                        entrySize = entry.file.length().coerceAtLeast(0L)
                        lastModifiedFileTime = entry.file.lastModified()
                        if (encrypt) {
                            isEncryptFiles = true
                            encryptionMethod = EncryptionMethod.AES
                            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                        } else {
                            isEncryptFiles = false
                        }
                    }
                }
                zos.putNextEntry(params)
                if (!entry.file.isDirectory) {
                    onProgress?.invoke(
                        ProgressUpdate(processedItems, totalItems, entry.file.name, processedBytes, totalBytes)
                    )
                    FileInputStream(entry.file).use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            zos.write(buffer, 0, read)
                            processedBytes += read.toLong()
                            onProgress?.invoke(
                                ProgressUpdate(processedItems, totalItems, entry.file.name, processedBytes, totalBytes)
                            )
                        }
                    }
                }
                zos.closeEntry()
                processedItems++
                onProgress?.invoke(
                    ProgressUpdate(processedItems, totalItems, entry.file.name, processedBytes, totalBytes)
                )
            }
        }
    }

    private suspend fun writeSevenZArchive(
        outputFile: File,
        entries: List<ArchiveEntrySource>,
        totalItems: Int,
        totalBytes: Long,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((ProgressUpdate) -> Unit)?
    ) {
        val method = when (compressionLevel) {
            ArchiveCompressionLevel.STORE -> SevenZMethod.COPY
            ArchiveCompressionLevel.FAST -> SevenZMethod.DEFLATE
            ArchiveCompressionLevel.NORMAL -> SevenZMethod.LZMA2
            ArchiveCompressionLevel.BEST -> SevenZMethod.BZIP2
        }
        var processedItems = 0
        var processedBytes = 0L
        val buffer = ByteArray(64 * 1024)

        SevenZOutputFile(outputFile).use { sevenZ ->
            sevenZ.setContentCompression(method)
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                val archiveName = if (entry.file.isDirectory) {
                    entry.archivePath.trimEnd('/')
                } else {
                    entry.archivePath
                }
                val sevenEntry = sevenZ.createArchiveEntry(entry.file, archiveName)
                sevenZ.putArchiveEntry(sevenEntry as ArchiveEntry)
                if (!entry.file.isDirectory) {
                    onProgress?.invoke(
                        ProgressUpdate(processedItems, totalItems, entry.file.name, processedBytes, totalBytes)
                    )
                    FileInputStream(entry.file).use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            sevenZ.write(buffer, 0, read)
                            processedBytes += read.toLong()
                            onProgress?.invoke(
                                ProgressUpdate(processedItems, totalItems, entry.file.name, processedBytes, totalBytes)
                            )
                        }
                    }
                }
                sevenZ.closeArchiveEntry()
                processedItems++
                onProgress?.invoke(
                    ProgressUpdate(processedItems, totalItems, entry.file.name, processedBytes, totalBytes)
                )
            }
            sevenZ.finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun openSevenZFile(file: File, password: String?): SevenZFile {
        return if (!password.isNullOrEmpty()) {
            SevenZFile(file, password.toCharArray())
        } else {
            SevenZFile(file)
        }
    }

    // endregion

    // region Helpers (Zip-Slip guard, Temp staging, SAF bridging)

    private fun matchesSelectedEntry(
        rawName: String,
        isDirectory: Boolean,
        selectedPaths: Set<String>
    ): Boolean {
        val clean = rawName.replace('\\', '/').trimStart('/').trimEnd('/')
        if (clean.isBlank()) return false
        for (sel in selectedPaths) {
            val cleanSel = sel.trimEnd('/')
            if (clean == cleanSel) return true
            if (clean.startsWith("$cleanSel/")) return true
            if (isDirectory && cleanSel.startsWith("$clean/")) return true
        }
        return false
    }

    private fun resolveOverwriteTarget(candidate: File, mode: ArchiveOverwriteMode): File? {
        if (!candidate.exists()) return candidate
        return when (mode) {
            ArchiveOverwriteMode.OVERWRITE -> candidate
            ArchiveOverwriteMode.SKIP -> null
            ArchiveOverwriteMode.RENAME -> {
                val parent = candidate.parentFile ?: return candidate
                val name = candidate.name
                val dot = name.lastIndexOf('.')
                val base = if (dot > 0) name.substring(0, dot) else name
                val ext = if (dot > 0) name.substring(dot) else ""
                var idx = 1
                var next = File(parent, "$base ($idx)$ext")
                while (next.exists()) {
                    idx++
                    next = File(parent, "$base ($idx)$ext")
                }
                next
            }
        }
    }

    private fun safeResolveEntry(outRoot: File, outCanonical: String, entryName: String): File? {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) return null
        val candidate = File(outRoot, normalized)
        val canon = candidate.canonicalPath
        if (canon != outCanonical && !canon.startsWith("$outCanonical${File.separator}")) {
            return null
        }
        return candidate
    }

    private fun sanitizeArchiveBaseName(raw: String): String {
        val cleaned = raw.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace(':', '_')
            .trim('.')
        return cleaned.ifBlank { "archive" }
    }

    private fun resolveUniqueArchiveFile(targetDir: File, baseName: String, ext: String): File {
        var candidate = File(targetDir, "$baseName.$ext")
        if (!candidate.exists()) return candidate
        var counter = 2
        while (candidate.exists()) {
            candidate = File(targetDir, "${baseName}_$counter.$ext")
            counter++
        }
        return candidate
    }

    private fun copySafToTempFile(item: FileItem): File {
        val ext = item.extension.lowercase().ifBlank { "bin" }
        val tmp = File.createTempFile("arc_inspect_", ".$ext", context.cacheDir)
        try {
            val stream: InputStream = context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
                ?: error("Cannot open SAF archive")
            stream.use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }
            return tmp
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private suspend fun stageSafItemToLocal(item: FileItem, targetParentDir: File): File {
        currentCoroutineContext().ensureActive()
        val dest = File(targetParentDir, item.name)
        if (!item.isDirectory) {
            val stream = context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
                ?: error("Cannot read '${item.name}'")
            stream.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
            return dest
        }

        dest.mkdirs()
        val treeUri = Uri.parse(item.pathOrUri)
        val docId = extractSafDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idIdx < 0) return@use
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val childId = cursor.getString(idIdx) ?: continue
                val childName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                    ?.takeIf { it.isNotBlank() }
                    ?: childId.substringAfterLast('/').substringAfterLast(':')
                val mime = (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null) ?: "application/octet-stream"
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                val childItem = FileItem(
                    id = childUri.toString(),
                    name = childName,
                    pathOrUri = childUri.toString(),
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    sizeBytes = 0L,
                    lastModifiedMillis = 0L,
                    mimeType = mime,
                    extension = childName.substringAfterLast('.', "").lowercase(),
                    isSafUri = true,
                    parentPathOrUri = item.pathOrUri
                )
                stageSafItemToLocal(childItem, dest)
            }
        }
        return dest
    }

    private suspend fun uploadDirectoryToSaf(localDir: File, safParentUri: Uri) {
        currentCoroutineContext().ensureActive()
        val children = localDir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
                val createdDir = createOrFindSafSubdir(safParentUri, child.name)
                uploadDirectoryToSaf(child, createdDir)
            } else {
                val ext = child.extension.lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                val createdFile = DocumentsContract.createDocument(
                    context.contentResolver,
                    normalizeSafDocumentUri(safParentUri),
                    mime,
                    child.name
                ) ?: error("Unable to create '${child.name}' in mounted folder")
                context.contentResolver.openOutputStream(createdFile)?.use { out ->
                    FileInputStream(child).use { input -> input.copyTo(out) }
                }
            }
        }
    }

    private fun createOrFindSafSubdir(parentUri: Uri, dirName: String): Uri {
        val docUri = normalizeSafDocumentUri(parentUri)
        val created = DocumentsContract.createDocument(
            context.contentResolver,
            docUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName
        )
        return created ?: error("Unable to create folder '$dirName' in mounted drive")
    }

    private fun extractSafDocumentId(uri: Uri): String {
        return runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }
    }

    private fun normalizeSafDocumentUri(uri: Uri): Uri {
        val docId = extractSafDocumentId(uri)
        return DocumentsContract.buildDocumentUriUsingTree(uri, docId)
    }

    private fun resolveSafDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    // endregion
}
