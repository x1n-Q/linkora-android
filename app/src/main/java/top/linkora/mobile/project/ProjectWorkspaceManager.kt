package top.linkora.mobile.project

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import org.json.JSONObject

class ProjectWorkspaceManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun restoreProject(): ProjectWorkspace = prepareProject(activeKind())

    fun prepareProject(kind: ProjectKind): ProjectWorkspace {
        val definition = definitionFor(kind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToDownloads(definition)
        } else {
            exportToAppDocuments(definition)
        }

        preferences.edit().putString(ACTIVE_KIND_KEY, kind.id).apply()
        val workspace = selectedTreeUri(kind)?.let { uri ->
            runCatching { buildTreeWorkspace(uri, kind) }.getOrElse {
                clearSelectedTree(kind)
                null
            }
        } ?: starterWorkspace(definition)
        activateLocalOrigin(kind)
        return workspace
    }

    fun selectProjectTree(uri: Uri): ProjectWorkspace {
        require(uri.scheme == "content") { "Choose a folder from Android Files." }
        val kind = activeKind()
        val workspace = buildTreeWorkspace(uri, kind)
        if (kind == ProjectKind.Node && selectedTreeUri(kind) != uri) {
            val runtimeDirectory = File(context.filesDir, NODE_RUNTIME_PROJECT_PATH)
            check(runtimeDirectory.deleteRecursively()) {
                "The previous private Node.js workspace could not be cleared."
            }
        }
        preferences.edit().putString(selectedTreeKey(kind), uri.toString()).apply()
        activateLocalOrigin(kind)
        return workspace
    }

    fun activeKind(): ProjectKind = ProjectKind.fromId(
        preferences.getString(ACTIVE_KIND_KEY, ProjectKind.Static.id),
    )

    fun prepareNodeLaunch(): NodeLaunchConfig {
        check(activeKind() == ProjectKind.Node) { "Choose a Node.js project first." }
        val runtimeDirectory = File(context.filesDir, NODE_RUNTIME_PROJECT_PATH)
        cleanNodeSource(runtimeDirectory)

        val selectedTree = selectedTreeUri(ProjectKind.Node)
        if (selectedTree != null) {
            val rootId = DocumentsContract.getTreeDocumentId(selectedTree)
            copyTree(selectedTree, rootId, runtimeDirectory, 0)
        } else {
            copyStarterToRuntime(NODE_DEFINITION, runtimeDirectory)
        }

        val inspection = inspectNodeDirectory(runtimeDirectory)
        return NodeLaunchConfig(
            projectDirectory = runtimeDirectory,
            entryPoint = inspection.entryPoint,
        )
    }

    fun openProjectFile(requestPath: String): ProjectFile? {
        val safePath = requestPath
            .replace('\\', '/')
            .trimStart('/')
            .ifBlank { "index.html" }
        if (safePath.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            return null
        }

        selectedTreeUri(ProjectKind.Static)?.let { treeUri ->
            return runCatching { openTreeFile(treeUri, safePath) }.getOrNull()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openDownloadFile(safePath)
        } else {
            openLegacyFile(safePath)
        }
    }

    private fun activateLocalOrigin(kind: ProjectKind) {
        if (kind == ProjectKind.Static) {
            ProjectServerService.start(context)
        } else {
            context.stopService(Intent(context, ProjectServerService::class.java))
        }
    }

    private fun starterWorkspace(definition: ProjectDefinition): ProjectWorkspace {
        val inspection = if (definition.kind == ProjectKind.Node) {
            NodeInspection(entryPoint = "server.js", hasDependencies = false)
        } else {
            null
        }
        return ProjectWorkspace(
            name = definition.displayName,
            visibleLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Download/LinkoraProjects/${definition.slug}"
            } else {
                legacyProjectDirectory(definition).absolutePath
            },
            files = definition.files,
            localUrl = LOCAL_URL,
            isStarter = true,
            kind = definition.kind,
            entryPoint = inspection?.entryPoint,
            hasDependencies = inspection?.hasDependencies ?: false,
        )
    }

    private fun selectedTreeUri(kind: ProjectKind): Uri? {
        val stored = preferences.getString(selectedTreeKey(kind), null)
            ?: if (kind == ProjectKind.Static) {
                preferences.getString(LEGACY_SELECTED_TREE_KEY, null)
            } else {
                null
            }
        return stored?.let(Uri::parse)
    }

    private fun selectedTreeKey(kind: ProjectKind) = "selected_tree_uri_${kind.id}"

    private fun clearSelectedTree(kind: ProjectKind) {
        preferences.edit()
            .remove(selectedTreeKey(kind))
            .apply {
                if (kind == ProjectKind.Static) remove(LEGACY_SELECTED_TREE_KEY)
            }
            .apply()
    }

    private fun buildTreeWorkspace(treeUri: Uri, kind: ProjectKind): ProjectWorkspace {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = queryDocument(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
        ) ?: error("The selected project folder is no longer available.")
        check(root.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            "Choose a project folder, not a single file."
        }

        val children = queryChildren(treeUri, root.id)
        val nodeInspection = when (kind) {
            ProjectKind.Static -> {
                check(children.any { it.name == "index.html" && !it.isDirectory }) {
                    "Choose a folder containing an index.html file."
                }
                null
            }

            ProjectKind.Node -> inspectNodeTree(treeUri)
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
            kind = kind,
            entryPoint = nodeInspection?.entryPoint,
            hasDependencies = nodeInspection?.hasDependencies ?: false,
        )
    }

    private fun inspectNodeTree(treeUri: Uri): NodeInspection {
        val manifest = readTreeText(treeUri, "package.json")?.let(::parseManifest)
        val candidates = buildList {
            manifest?.startEntry?.let(::add)
            manifest?.main?.let(::add)
            addAll(NODE_ENTRY_CANDIDATES)
        }
        val entryPoint = candidates
            .mapNotNull(::safeRelativePath)
            .distinct()
            .firstOrNull { treeFileExists(treeUri, it) }
            ?: error(
                "Choose a Node.js folder containing package.json and a runnable " +
                    "server.js, index.js, app.js, .mjs, or .cjs entry file.",
            )
        return NodeInspection(
            entryPoint = entryPoint,
            hasDependencies = manifest?.hasDependencies == true,
        )
    }

    private fun inspectNodeDirectory(directory: File): NodeInspection {
        val manifest = File(directory, "package.json")
            .takeIf(File::isFile)
            ?.readText()
            ?.let(::parseManifest)
        val candidates = buildList {
            manifest?.startEntry?.let(::add)
            manifest?.main?.let(::add)
            addAll(NODE_ENTRY_CANDIDATES)
        }
        val root = directory.canonicalFile
        val entryPoint = candidates
            .mapNotNull(::safeRelativePath)
            .distinct()
            .firstOrNull { candidate ->
                val file = File(root, candidate).canonicalFile
                file.isFile && file.path.startsWith("${root.path}${File.separator}")
            }
            ?: error("No supported Node.js entry file was found after project sync.")
        return NodeInspection(
            entryPoint = entryPoint,
            hasDependencies = manifest?.hasDependencies == true,
        )
    }

    private fun parseManifest(contents: String): NodeManifest {
        val manifest = runCatching { JSONObject(contents) }
            .getOrElse { error("package.json is not valid JSON.") }
        val startScript = manifest.optJSONObject("scripts")?.optString("start")
        val startEntry = startScript
            ?.let { NODE_START_PATTERN.matchEntire(it)?.groupValues?.get(1) }
        val dependencies = manifest.optJSONObject("dependencies")
        val developmentDependencies = manifest.optJSONObject("devDependencies")
        val optionalDependencies = manifest.optJSONObject("optionalDependencies")
        return NodeManifest(
            main = manifest.optString("main").takeIf(String::isNotBlank),
            startEntry = startEntry,
            hasDependencies = (dependencies?.length() ?: 0) > 0 ||
                (developmentDependencies?.length() ?: 0) > 0 ||
                (optionalDependencies?.length() ?: 0) > 0,
        )
    }

    private fun safeRelativePath(path: String): String? {
        val normalized = path.replace('\\', '/').removePrefix("./")
        if (normalized.isBlank() || normalized.startsWith('/')) return null
        if (normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return normalized.takeIf {
            it.endsWith(".js", ignoreCase = true) ||
                it.endsWith(".mjs", ignoreCase = true) ||
                it.endsWith(".cjs", ignoreCase = true)
        }
    }

    private fun readTreeText(treeUri: Uri, path: String): String? =
        findTreeFile(treeUri, path)?.let { entry ->
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id)
            context.contentResolver.openInputStream(documentUri)?.bufferedReader()?.use { it.readText() }
        }

    private fun treeFileExists(treeUri: Uri, path: String): Boolean =
        findTreeFile(treeUri, path)?.isDirectory == false

    private fun findTreeFile(treeUri: Uri, path: String): DocumentEntry? {
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
        return selected
    }

    private fun openTreeFile(treeUri: Uri, path: String): ProjectFile? {
        val file = findTreeFile(treeUri, path)?.takeUnless(DocumentEntry::isDirectory) ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.id)
        val stream = context.contentResolver.openInputStream(documentUri) ?: return null
        return ProjectFile(
            input = stream,
            length = file.size,
            mimeType = file.mimeType.takeUnless { it == "application/octet-stream" }
                ?: file.name.mimeType(),
        )
    }

    private fun copyTree(treeUri: Uri, parentId: String, destination: File, depth: Int) {
        check(depth <= MAX_TREE_DEPTH) { "The project folder is nested too deeply." }
        check(destination.mkdirs() || destination.isDirectory) {
            "The private Node.js workspace could not be created."
        }
        queryChildren(treeUri, parentId).forEach { entry ->
            if (entry.name in IGNORED_NODE_DIRECTORIES) return@forEach
            check(entry.name.isNotBlank() && '/' !in entry.name && '\\' !in entry.name) {
                "The project contains an unsupported file name."
            }
            val output = File(destination, entry.name)
            if (entry.isDirectory) {
                copyTree(treeUri, entry.id, output, depth + 1)
            } else {
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id)
                checkNotNull(context.contentResolver.openInputStream(documentUri)).use { input ->
                    output.outputStream().use { stream -> input.copyTo(stream) }
                }
            }
        }
    }

    private fun cleanNodeSource(runtimeDirectory: File) {
        check(runtimeDirectory.mkdirs() || runtimeDirectory.isDirectory) {
            "The private Node.js workspace could not be created."
        }
        runtimeDirectory.listFiles().orEmpty().forEach { child ->
            if (child.name != "node_modules") {
                check(child.deleteRecursively()) {
                    "The previous private Node.js source could not be cleared."
                }
            }
        }
    }

    private fun copyStarterToRuntime(definition: ProjectDefinition, destination: File) {
        definition.files.forEach { relativePath ->
            val output = File(destination, relativePath)
            check(output.parentFile?.mkdirs() != false || output.parentFile?.isDirectory == true)
            val input = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val parent = relativePath.substringBeforeLast('/', "")
                val fileName = relativePath.substringAfterLast('/')
                val downloadPath = definition.downloadPath +
                    if (parent.isBlank()) "" else "$parent/"
                val uri = checkNotNull(findDownloadUri(fileName, downloadPath)) {
                    "The editable Node.js starter file $relativePath is missing from Downloads."
                }
                checkNotNull(context.contentResolver.openInputStream(uri))
            } else {
                File(legacyProjectDirectory(definition), relativePath).inputStream()
            }
            input.use { source ->
                output.outputStream().use { stream -> source.copyTo(stream) }
            }
        }
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
    private fun exportToDownloads(definition: ProjectDefinition) {
        definition.files.forEach { relativeFile ->
            val parent = relativeFile.substringBeforeLast('/', "")
            val fileName = relativeFile.substringAfterLast('/')
            val relativePath = definition.downloadPath + if (parent.isBlank()) "" else "$parent/"
            if (findDownloadUri(fileName, relativePath) != null) return@forEach

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, fileName.mimeType())
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = checkNotNull(
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
            ) { "The starter project could not be created in Downloads." }

            try {
                context.assets.open("${definition.assetRoot}/$relativeFile").use { source ->
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
        val relativePath = STATIC_DEFINITION.downloadPath + if (parent.isBlank()) "" else "$parent/"
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

    private fun exportToAppDocuments(definition: ProjectDefinition) {
        val directory = legacyProjectDirectory(definition).apply {
            check(mkdirs() || isDirectory) { "The project folder could not be created." }
        }
        definition.files.forEach { relativePath ->
            val destination = File(directory, relativePath)
            check(destination.parentFile?.mkdirs() != false || destination.parentFile?.isDirectory == true)
            if (!destination.exists()) {
                context.assets.open("${definition.assetRoot}/$relativePath").use { source ->
                    destination.outputStream().use { output -> source.copyTo(output) }
                }
            }
        }
    }

    private fun openLegacyFile(path: String): ProjectFile? {
        val root = legacyProjectDirectory(STATIC_DEFINITION).canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith("${root.path}${File.separator}") || !file.isFile) return null
        return ProjectFile(
            input = file.inputStream(),
            length = file.length(),
            mimeType = file.name.mimeType(),
        )
    }

    private fun legacyProjectDirectory(definition: ProjectDefinition): File {
        val root = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS))
        return File(root, "LinkoraProjects/${definition.slug}")
    }

    private fun definitionFor(kind: ProjectKind): ProjectDefinition = when (kind) {
        ProjectKind.Static -> STATIC_DEFINITION
        ProjectKind.Node -> NODE_DEFINITION
    }

    private fun String.mimeType(): String = when (substringAfterLast('.').lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs", "cjs" -> "text/javascript; charset=utf-8"
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
        const val LOCAL_PORT = 8787
        const val LOCAL_URL = "http://127.0.0.1:$LOCAL_PORT"

        private val STATIC_DEFINITION = ProjectDefinition(
            kind = ProjectKind.Static,
            slug = "linkora-starter",
            displayName = "Linkora Starter",
            assetRoot = "starter-site",
            files = listOf("index.html", "styles.css", "app.js", "linkora-mark.svg"),
        )
        private val NODE_DEFINITION = ProjectDefinition(
            kind = ProjectKind.Node,
            slug = "linkora-node-sample",
            displayName = "Linkora Node Sample",
            assetRoot = "starter-node",
            files = listOf(
                "package.json",
                "server.js",
                "public/index.html",
                "public/styles.css",
                "public/app.js",
                "public/linkora-mark.svg",
            ),
        )
        private val NODE_ENTRY_CANDIDATES = listOf(
            "server.js",
            "server.mjs",
            "server.cjs",
            "index.js",
            "index.mjs",
            "index.cjs",
            "app.js",
            "app.mjs",
            "app.cjs",
        )
        private val NODE_START_PATTERN = Regex("^\\s*node\\s+([A-Za-z0-9_./-]+)\\s*$")
        private val IGNORED_NODE_DIRECTORIES = setOf("node_modules", ".git", ".linkora")
        private const val NODE_RUNTIME_PROJECT_PATH = "node/projects/active"
        private const val PREFERENCES = "project_workspace"
        private const val ACTIVE_KIND_KEY = "active_project_kind"
        private const val LEGACY_SELECTED_TREE_KEY = "selected_tree_uri"
        private const val MAX_VISIBLE_FILES = 24
        private const val MAX_TREE_DEPTH = 32
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}

private data class ProjectDefinition(
    val kind: ProjectKind,
    val slug: String,
    val displayName: String,
    val assetRoot: String,
    val files: List<String>,
) {
    val downloadPath: String
        get() = "Download/LinkoraProjects/$slug/"
}

private data class NodeManifest(
    val main: String?,
    val startEntry: String?,
    val hasDependencies: Boolean,
)

private data class NodeInspection(
    val entryPoint: String,
    val hasDependencies: Boolean,
)

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
