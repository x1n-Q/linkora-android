package top.linkora.mobile.project

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream

class ProjectWorkspaceManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun ensureStarterProject(): ProjectWorkspace {
        File(context.filesDir, "projects/$PROJECT_SLUG").deleteRecursively()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToDownloads()
        } else {
            exportToAppDocuments()
        }

        val workspace = selectedTreeUri()
            ?.let { uri -> runCatching { buildTreeWorkspace(uri) }.getOrNull() }
            ?: starterWorkspace()
        ProjectServerService.start(context)
        return workspace
    }

    fun selectProjectTree(uri: Uri): ProjectWorkspace {
        require(uri.scheme == "content") { "Choose a folder from Android Files." }
        val workspace = buildTreeWorkspace(uri)
        preferences.edit().putString(SELECTED_TREE_KEY, uri.toString()).apply()
        ProjectServerService.start(context)
        return workspace
    }

    fun openProjectFile(requestPath: String): ProjectFile? {
        val safePath = requestPath
            .replace('\\', '/')
            .trimStart('/')
            .ifBlank { "index.html" }
        if (safePath.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            return null
        }

        selectedTreeUri()?.let { treeUri ->
            return runCatching { openTreeFile(treeUri, safePath) }.getOrNull()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openDownloadFile(safePath)
        } else {
            openLegacyFile(safePath)
        }
    }

    private fun starterWorkspace() = ProjectWorkspace(
        name = "Linkora Starter",
        visibleLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Download/LinkoraProjects/$PROJECT_SLUG"
        } else {
            legacyProjectDirectory().absolutePath
        },
        files = SAMPLE_FILES,
        localUrl = LOCAL_URL,
        isStarter = true,
    )

    private fun selectedTreeUri(): Uri? = preferences.getString(SELECTED_TREE_KEY, null)
        ?.let(Uri::parse)

    private fun buildTreeWorkspace(treeUri: Uri): ProjectWorkspace {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = queryDocument(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
        ) ?: error("The selected project folder is no longer available.")
        check(root.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            "Choose a project folder, not a single file."
        }

        val children = queryChildren(treeUri, root.id)
        check(children.any { it.name == "index.html" && !it.isDirectory }) {
            "Choose a folder containing an index.html file."
        }

        return ProjectWorkspace(
            name = root.name.ifBlank { "Local project" },
            visibleLocation = treeLocation(treeUri),
            files = children
                .sortedWith(compareBy<DocumentEntry>({ !it.isDirectory }, { it.name.lowercase() }))
                .take(MAX_VISIBLE_FILES)
                .map { if (it.isDirectory) "${it.name}/" else it.name },
            localUrl = LOCAL_URL,
            isStarter = false,
        )
    }

    private fun openTreeFile(treeUri: Uri, path: String): ProjectFile? {
        var parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val segments = path.split('/')
        var selected: DocumentEntry? = null

        segments.forEachIndexed { index, segment ->
            val entry = queryChildren(treeUri, parentId).firstOrNull { it.name == segment }
                ?: return null
            selected = entry
            if (index < segments.lastIndex) {
                if (!entry.isDirectory) return null
                parentId = entry.id
            }
        }

        val file = selected?.takeUnless(DocumentEntry::isDirectory) ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.id)
        val stream = context.contentResolver.openInputStream(documentUri) ?: return null
        return ProjectFile(
            input = stream,
            length = file.size,
            mimeType = file.mimeType.takeUnless { it == "application/octet-stream" }
                ?: file.name.mimeType(),
        )
    }

    private fun queryDocument(documentUri: Uri): DocumentEntry? =
        context.contentResolver.query(documentUri, DOCUMENT_COLUMNS, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DocumentEntry(
                id = cursor.getString(0),
                name = cursor.getString(1).orEmpty(),
                mimeType = cursor.getString(2).orEmpty(),
                size = if (cursor.isNull(3)) -1L else cursor.getLong(3),
            )
        }

    private fun queryChildren(treeUri: Uri, parentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return context.contentResolver.query(childrenUri, DOCUMENT_COLUMNS, null, null, null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DocumentEntry(
                                id = cursor.getString(0),
                                name = cursor.getString(1).orEmpty(),
                                mimeType = cursor.getString(2).orEmpty(),
                                size = if (cursor.isNull(3)) -1L else cursor.getLong(3),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    private fun treeLocation(treeUri: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return when {
            documentId.startsWith("primary:") ->
                "Internal storage/${documentId.removePrefix("primary:")}"
            documentId.contains(':') -> documentId.substringAfter(':')
            else -> documentId
        }.trimEnd('/')
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToDownloads() {
        SAMPLE_FILES.forEach { fileName ->
            if (findDownloadUri(fileName, DOWNLOAD_RELATIVE_PATH) != null) return@forEach

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, fileName.mimeType())
                put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = checkNotNull(
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
            ) { "The starter project could not be created in Downloads." }

            try {
                context.assets.open("starter-site/$fileName").use { source ->
                    checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
                        source.copyTo(output)
                    }
                }
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openDownloadFile(path: String): ProjectFile? {
        val parent = path.substringBeforeLast('/', "")
        val fileName = path.substringAfterLast('/')
        val relativePath = DOWNLOAD_RELATIVE_PATH + if (parent.isBlank()) "" else "$parent/"
        val uri = findDownloadUri(fileName, relativePath) ?: return null
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return null
        val stream = context.contentResolver.openInputStream(uri) ?: run {
            descriptor.close()
            return null
        }
        return ProjectFile(
            input = object : FilterInputStream(stream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        descriptor.close()
                    }
                }
            },
            length = descriptor.length,
            mimeType = fileName.mimeType(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findDownloadUri(fileName: String, relativePath: String): Uri? =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(fileName, relativePath),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
            } else {
                null
            }
        }

    private fun exportToAppDocuments() {
        val directory = legacyProjectDirectory().apply {
            check(mkdirs() || isDirectory) { "The project folder could not be created." }
        }
        SAMPLE_FILES.forEach { fileName ->
            val destination = File(directory, fileName)
            if (!destination.exists()) {
                context.assets.open("starter-site/$fileName").use { source ->
                    destination.outputStream().use { output -> source.copyTo(output) }
                }
            }
        }
    }

    private fun openLegacyFile(path: String): ProjectFile? {
        val root = legacyProjectDirectory().canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith("${root.path}${File.separator}") || !file.isFile) return null
        return ProjectFile(
            input = file.inputStream(),
            length = file.length(),
            mimeType = file.name.mimeType(),
        )
    }

    private fun legacyProjectDirectory(): File {
        val root = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS))
        return File(root, "LinkoraProjects/$PROJECT_SLUG")
    }

    private fun String.mimeType(): String = when (substringAfterLast('.').lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "txt" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    companion object {
        const val PROJECT_SLUG = "linkora-starter"
        const val LOCAL_PORT = 8787
        const val LOCAL_URL = "http://127.0.0.1:$LOCAL_PORT"
        const val DOWNLOAD_RELATIVE_PATH = "Download/LinkoraProjects/$PROJECT_SLUG/"
        val SAMPLE_FILES = listOf("index.html", "styles.css", "app.js", "linkora-mark.svg")

        private const val PREFERENCES = "project_workspace"
        private const val SELECTED_TREE_KEY = "selected_tree_uri"
        private const val MAX_VISIBLE_FILES = 24
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}

private data class DocumentEntry(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

class ProjectFile(
    val input: InputStream,
    val length: Long,
    val mimeType: String,
) : AutoCloseable {
    override fun close() = input.close()
}
