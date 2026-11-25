package com.example.data.db

import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.EffectiveFolderLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FolderLayoutRepository(private val dao: FolderLayoutDao) {

    fun observeAllLayouts(): Flow<Map<String, FolderLayoutEntity>> =
        dao.observeAll().map { entities ->
            entities.associateBy { it.folderKey }
        }

    fun observeLayout(folderKey: String): Flow<FolderLayoutEntity?> =
        dao.observeByKey(normalizeKey(folderKey))

    suspend fun getLayout(folderKey: String): FolderLayoutEntity? = withContext(Dispatchers.IO) {
        dao.getByKey(normalizeKey(folderKey))
    }

    suspend fun saveLayout(
        folderKey: String,
        layout: EffectiveFolderLayout,
        updatedAtMillis: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val cleanKey = normalizeKey(folderKey)
        dao.upsert(FolderLayoutEntity.fromEffectiveLayout(cleanKey, layout, updatedAtMillis))
    }

    /**
     * Removes saved layout settings for a deleted or renamed folder and any saved child subfolders.
     */
    suspend fun removeFolderState(pathOrUri: String): Int = withContext(Dispatchers.IO) {
        val targetKey = normalizeKey(pathOrUri)
        if (targetKey.isBlank()) return@withContext 0

        val allKeys = dao.getAllKeys()
        if (allKeys.isEmpty()) return@withContext 0

        val targetSafDocId = extractSafDocIdIfContentUri(targetKey)
        val keysToDelete = allKeys.filter { candidate ->
            isSameOrDescendantFolderKey(candidate, targetKey, targetSafDocId)
        }
        if (keysToDelete.isEmpty()) {
            0
        } else {
            dao.deleteByKeys(keysToDelete)
        }
    }

    suspend fun clearAllLayouts(): Int = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    internal fun isSameOrDescendantFolderKey(
        candidateKey: String,
        targetKey: String,
        targetSafDocId: String? = extractSafDocIdIfContentUri(targetKey)
    ): Boolean {
        val cleanCandidate = normalizeKey(candidateKey)
        if (cleanCandidate == targetKey) return true
        if (!targetKey.startsWith("content://") && cleanCandidate.startsWith("$targetKey/")) {
            return true
        }
        if (targetKey.startsWith("content://") &&
            (cleanCandidate.startsWith("$targetKey/") || cleanCandidate.startsWith("$targetKey%2F", ignoreCase = true))
        ) {
            return true
        }
        if (targetSafDocId != null && cleanCandidate.startsWith("content://")) {
            val candidateDocId = extractSafDocIdIfContentUri(cleanCandidate)
            if (candidateDocId != null) {
                if (candidateDocId == targetSafDocId) return true
                if (candidateDocId.startsWith("$targetSafDocId/")) return true
            }
        }
        return false
    }

    private fun extractSafDocIdIfContentUri(value: String): String? {
        if (!value.startsWith("content://")) return null
        return runCatching {
            val uri = Uri.parse(value)
            runCatching { DocumentsContract.getDocumentId(uri) }
                .getOrElse { runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() }
        }.getOrNull()
    }

    private fun normalizeKey(raw: String): String {
        return if (!raw.startsWith("content://") && raw.length > 1) {
            raw.trimEnd('/')
        } else {
            raw
        }
    }
}
